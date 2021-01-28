package com.nchain.jcl.store.keyValue.blockChainStore;

import com.nchain.jcl.base.domain.api.base.BlockHeader;
import com.nchain.jcl.base.domain.api.extended.ChainInfo;
import com.nchain.jcl.base.tools.crypto.Sha256Wrapper;
import com.nchain.jcl.store.blockChainStore.BlockChainStore;
import com.nchain.jcl.store.blockChainStore.BlockChainStoreState;
import com.nchain.jcl.store.blockChainStore.events.ChainForkEvent;
import com.nchain.jcl.store.blockChainStore.events.ChainPruneEvent;
import com.nchain.jcl.store.blockChainStore.events.ChainStateEvent;
import com.nchain.jcl.store.keyValue.blockStore.BlockStoreKeyValue;
import com.nchain.jcl.store.keyValue.common.HashesList;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author i.fernandez@nchain.com
 * Copyright (c) 2018-2020 nChain Ltd
 *
 * Thsi interface extends teh "BlockStore" and adds capabilities to store and traverse the Chain of Blocks, and
 * also detected Forks (and prune them).
 *
 * In additiona to all the infor already sotred by the "BlockStore" interface, this one adds some more.
 * A Block has now other Entries, all under the "BLOCKS" directory:
 *
 *  "b:[blockHash]:next:    Stores a LIST of those Blcoks built on top of this one. Its usually a one-element list, but
 *                          in case of a FORK there might be more than one.
 *
 *  "b:chain:[blockHash]":  an instance of b.lockChainInfo. If this Block can be CONNECTED to the Chain (meaning that
 *                          its parent is also stored and connected to the Chain), then this isntance stores the
 *                          relative chain info for this Block.*
 *
 *  Also, there is a new Key (in the "BLOCKCHAIN" directory) that stores a List with the TIPS of the Chain
 *  (Block Hashes). Its usually a one-element List, but in case of a FORK it might contain more than one element.
 *
 * "chain_tips": a single property that stores the list of the current TIPS of all the chains stored
 *
 * This class also intrudices the concept of PATH:
 * a PAth is a series of blocks that make up a LINE of Blocks, from the parent (the FIRST), to the last. A Path is
 * always a Stright ine: no Forks are allowed.
 * Example:
 *      - Considering this series of Blocks: [A] - [B] - [C]
 *      - All the blocks in this example have the same Path. Every PAth has a PathId, in this case lets say is TWO (2).
 *        So we can print the same example adding the Path id to each block:
 *        [A:2] - [B:2] - [C:2]
 *      - If we insert a Block [X], which is also a "children" of [B], ten we are creating a FORK. At this momento, the
 *        PAth is split into 2, resultng into this:
 *        [A:2] - [B:2] - [C:3]
 *                     |- [X:4]
 *        So now we have 3 Paths, each one of them is a straight line of blocks.
 *
 *        For each Path that is created this way, we also store the relationship between this PAth and its "parentPath",
 *        in this case:
 *          - Path #2 has nor parent in this example
 *          - Path #3 has a Parent Path #2
 *          - Path #4 has a Parent PAth #2
 *
 *  "chain_path:[pathId]":  This propery stored the info for one specific Path.
 *  "chain_paths:next:"     This sores the Id of the Last Path used Since the PAths are created in Real-time whenever a Fork
 *                          is detected, this property is used to pick up the next Path Id.
 *
 **
 * @param <E>   Type of each ENTRY in the DB. Each Key-Value DB implementation usually provides Iterators that returns
 *              Entries from the DB (KeyValue in FoundationDB, a Map.Entry in LevelDb, etc).
 * @param <T>   Type of the TRANSACTION used by the DB-specific implementation. If Transactions are NOT supported, the
 *              "Object" Type can be used.
 */
public interface BlockChainStoreKeyValue<E, T> extends BlockStoreKeyValue<E, T>, BlockChainStore {

    /** Configuration: */
    BlockChainStoreKeyValueConfig getConfig();

    // Keys used to store block info and it's relative position within the Chain:
    String KEY_SUFFIX_BLOCK_NEXT     = "next";         // Block built on top of this one
    String KEY_PREFFIX_BLOCK_CHAIN   = "b_chain";      // Chan info for this block
    String KEY_CHAIN_TIPS            = "chain_tips";  // List of all the Tip Chains

    String KEY_PREFFIX_PATHS         = "chain_paths";
    String KEY_SUFFIX_PATHS_LAST     = "last";
    String KEY_PREFFIX_PATH          = "chain_path";



    /* Functions to generate Simple Keys in String format: */

    default String keyForBlockNext(String blockHash)        { return KEY_PREFFIX_BLOCK_PROP + blockHash + KEY_SEPARATOR + KEY_SUFFIX_BLOCK_NEXT + KEY_SEPARATOR; }
    default String keyForBlockChainInfo(String blockHash)   { return KEY_PREFFIX_BLOCK_CHAIN + KEY_SEPARATOR + blockHash + KEY_SEPARATOR; }
    default String keyForChainTips()                        { return KEY_CHAIN_TIPS + KEY_SEPARATOR; }
    default String keyForChainPathsLast()                   { return KEY_PREFFIX_PATHS + KEY_SEPARATOR + KEY_SUFFIX_PATHS_LAST + KEY_SEPARATOR;}
    default String keyForChainPath(int branchId)            { return KEY_PREFFIX_PATH + KEY_SEPARATOR + branchId + KEY_SEPARATOR;}

    /* Functions to generate WHOLE Keys, from the root up to the item. to be implemented by specific DB provider */

    byte[] fullKeyForBlockNext(String blockHash);
    byte[] fullKeyForBlockChainInfo(String blockHash);
    byte[] fullKeyForChainTips();
    byte[] fullKeyForChainPathsLast();
    byte[] fullKeyForChainPath(int branchId);

    /* Functions to serialize Objects:  */

    default byte[] bytes(BlockChainInfo blockChainInfo)     { return BlockChainInfoSerializer.getInstance().serialize(blockChainInfo); }
    default byte[] bytes(ChainPathInfo chainBranchInfo)   { return ChainPathInfoSerializer.getInstance().serialize(chainBranchInfo);}

    /* Functions to deserialize Objects: */

    default BlockChainInfo  toBlockChainInfo(byte[] bytes)  { return (isBytesOk(bytes)) ? BlockChainInfoSerializer.getInstance().deserialize(bytes) : null;}
    default ChainPathInfo   toChainPathInfo(byte[] bytes) { return (isBytesOk(bytes)) ? ChainPathInfoSerializer.getInstance().deserialize(bytes) : null;}

    /*
     BlockChain Store DB Operations:
     These methods execute the business logic. Most of the time, each one of the methods below map a method of the
     BlockStore interface, but with some peculiarities:
     - They do NOT trigger Events
     - They do NOT crete new DB Transaction, instead they need to reuse one passed as a parameter.

     The Events and Transactions are created at a higher-level (byt he public methods that implemen the BlockStore
     interface).
     */

    private BlockChainInfo _getBlockChainInfo(T tr, String blockHash) {
        byte[] value = read(tr, fullKeyForBlockChainInfo(blockHash));
        return toBlockChainInfo(value);
    }

    private void _saveBlockChainInfo(T tr, BlockChainInfo blockChainInfo) {
        byte[] key = fullKeyForBlockChainInfo(blockChainInfo.getBlockHash());
        byte[] value = bytes(blockChainInfo);
        save(tr, key, value);
        getLogger().trace("BlockChainInfo Saved/Updated [block: " + blockChainInfo.getBlockHash().toString() + ", path: " + blockChainInfo.getChainPathId() + ", height: " + blockChainInfo.getHeight() + "]");
    }

    private void _removeBlockChainInfo(T tr, String blockHash) {
        remove(tr, fullKeyForBlockChainInfo(blockHash));
    }

    private BlockChainInfo _saveBlockChainInfo(T tr, BlockHeader block, BlockChainInfo parentBlockChainInfo, int chainPathId) {

        // We calculate the Height of the Chain:
        int resultHeight = (parentBlockChainInfo != null)
                ? parentBlockChainInfo.getHeight() + 1
                : 0;
        // We calculate the Size Inn Bytes of the Chain:
        // TODO: Possible overflow here????
        long resultChainSize = (parentBlockChainInfo != null)
                ? block.getSizeInBytes() + parentBlockChainInfo.getTotalChainSize()
                : block.getSizeInBytes();

        // We set the value of the ChainWork:
        BigInteger chainWork = (parentBlockChainInfo != null)
                                ? parentBlockChainInfo.getChainWork().add(block.getWork())
                                : getConfig().getGenesisBlock().getWork();

        // We build the object and save it:
        BlockChainInfo blockChainInfo = BlockChainInfo.builder()
                .blockHash(block.getHash().toString())
                .chainWork(chainWork)
                .height(resultHeight)
                .totalChainSize(resultChainSize)
                .chainPathId(chainPathId)
                .build();

        _saveBlockChainInfo(tr, blockChainInfo);
        return blockChainInfo;
    }

    private List<String> _getNextBlocks(T tr, String blockHash) {
        List<String> result = new ArrayList<>();
        HashesList nextBlocks = toHashes(read(tr, fullKeyForBlockNext(blockHash)));
        if (nextBlocks != null) result.addAll(nextBlocks.getHashes());
        return result;
    }

    private List<BlockChainInfo> _getNextConnectedBlocks(T tr, String blockHash) {
        List<BlockChainInfo> result = new ArrayList<>();
        List<String> nextBlocks = _getNextBlocks(tr, blockHash);
        for (String childBlockHash : nextBlocks) {
            BlockChainInfo childChainInfo = _getBlockChainInfo(tr, childBlockHash);
            if (childChainInfo != null) result.add(childChainInfo);
        }
        return result;
    }

    private void _addChildToBlock(T tr, String parentBlockHash, String childBlockHash) {
        List<String> childs = _getNextBlocks(tr, parentBlockHash);
        if (!(childs.contains(childBlockHash)))
            childs.add(childBlockHash);
        HashesList childListToStore = HashesList.builder().hashes(childs).build();
        save(tr, fullKeyForBlockNext(parentBlockHash), bytes(childListToStore));
        getLogger().trace("Block " + childBlockHash + " saved as a CHILD of " + parentBlockHash);
    }

    private void _removeChildFromBlock(T tr, String parentBlockHash, String childBlockHash) {
        List<String> childs = _getNextBlocks(tr, parentBlockHash);
        childs.remove(childBlockHash);
        if (childs.size() > 0) {
            HashesList childsToStore = HashesList.builder().hashes(childs).build();
            save(tr, fullKeyForBlockNext(parentBlockHash), bytes(childsToStore));
        }  else    remove(tr, fullKeyForBlockNext(parentBlockHash));
        getLogger().trace("Block " + childBlockHash + " removed as CHILD from parent " + parentBlockHash);
    }

    default List<String> _getChainTips(T tr) {
        List<String> result = new ArrayList<>();
        HashesList tips = toHashes(read(tr, fullKeyForChainTips()));
        if (tips != null) result.addAll(tips.getHashes());
        return result;
    }

    private void _saveChainTips(T tr, HashesList chainstips) {
        save(tr, fullKeyForChainTips(), bytes(chainstips));
    }

    private void _updateTipsChain(T tr, String blockHashToAdd, String blockHashToRemove) {
        List<String> tipsChain = _getChainTips(tr);
        if ((blockHashToAdd != null)  && (!tipsChain.contains(blockHashToAdd))) {
            tipsChain.add(blockHashToAdd);
            getLogger().trace("Block "+ blockHashToAdd + " added to the Tips");
        }
        if (blockHashToRemove != null && tipsChain.contains(blockHashToRemove))  {
            tipsChain.remove(blockHashToRemove);
            getLogger().trace("Block "+ blockHashToRemove + " removed from the Tips");
        }
        HashesList tipsToSave = HashesList.builder().hashes(tipsChain).build();
        _saveChainTips(tr, tipsToSave);
    }

    private void _connectBlock(T tr, BlockHeader blockHeader, BlockChainInfo parentBlockChainInfo) {

        getLogger().trace("Connecting Block " + blockHeader.getHash().toString() + " ...");
        // Block chain Info that will be inserted for this Block:
        BlockChainInfo blockChainInfo;

        // Special case for the Genesis Block:
        if (parentBlockChainInfo == null) {
            blockChainInfo = _saveBlockChainInfo(tr, blockHeader, parentBlockChainInfo, 1);
        } else {
            // Regular scenario, when connecting a Block to an existing Parent:
            //  - If the parent has NO children we just connect the Block and REUSE the parent's PathId
            //  - If the Parent has ONE Child, then this is the First FORk starting from that Parent, so we create
            //    2 new Paths: 1 is assigned to the old child, and the new one to the block we are connecting now.
            //  - If the parent has already MOR than 1 child, that means that there is already a FORK starting from this
            //    parent, so its children are already using different Paths. In this case we only need to create one additional
            //    path and assign it to the Block we are connecting.

            int pathIdForNewBlock = parentBlockChainInfo.getChainPathId();
            List<BlockChainInfo> parentConnectedChildren = _getNextConnectedBlocks(tr, parentBlockChainInfo.getBlockHash());

            if (parentConnectedChildren.size() > 0) {
                // if there is only ONE Child, we update its PathId with a new one...
                // If there are MORE than one children, then they must have already different Paths Id, so nothing to do...

                if (parentConnectedChildren.size() == 1) {
                    int newChainPathToPropagate = _createNewChainPath(tr, parentBlockChainInfo.getChainPathId(), parentConnectedChildren.get(0).getBlockHash()).getId();
                    _propagateChainPathUnderBlock(tr, parentConnectedChildren.get(0), parentConnectedChildren.get(0).getChainPathId(), newChainPathToPropagate);
                }
                // We create a new Path Id for the block we are connecting...
                pathIdForNewBlock =  _createNewChainPath(tr, parentBlockChainInfo.getChainPathId(), blockHeader.getHash().toString()).getId();
            }
            // We save and connect this block
            blockChainInfo = _saveBlockChainInfo(tr, blockHeader, parentBlockChainInfo, pathIdForNewBlock);
        }

        // Now we update the tips of the Chain:
        List<String> tipsChain = _getChainTips(tr);

        // If the Parent is part of the TIPS of the Chains, then it must be removed from it:
        if (parentBlockChainInfo != null && tipsChain.contains(parentBlockChainInfo.getBlockHash())) {
            _updateTipsChain(tr, null, parentBlockChainInfo.getBlockHash()); // we don't add, just remove
        }

        // Now we look into the CHILDREN (Blocks built on top of this Block), and we connect them as well...
        // If the Block has NOT Children, then this is the Last Block that can be connected, so we add it to the Tips
        List<String> children = _getNextBlocks(tr, blockHeader.getHash().toString());
        if (children != null && children.size() > 0) {
            for (String childHashHex : children) {
                Optional<BlockHeader> childBlock = getBlock(Sha256Wrapper.wrap(childHashHex));
                if (childBlock.isPresent()) _connectBlock(tr, childBlock.get(), blockChainInfo);
            }
        } else {
            _updateTipsChain(tr, blockHeader.getHash().toString(), null); // We add this block to the Tips
        }
    }

    private void _disconnectBlock(T tr, String blockHash) {
        // If this block is already connected we remove the Chain Info:
        BlockChainInfo blockChainInfo = _getBlockChainInfo(tr, blockHash);
        if (blockChainInfo != null) {
            BlockHeader block = _getBlock(tr, blockHash);
            getLogger().trace("Disconnecting Block " + blockChainInfo.getBlockHash() + "(height: " + blockChainInfo.getHeight() + ") (path: " + blockChainInfo.getChainPathId() + ")...");

            // We remove the ChainInfo for this Node (this will disconnect this Block from the Chain):
            _removeBlockChainInfo(tr, blockHash);

            // Now we need to check if the Path used by this Block can be removed or not. It can be removed if:
            // - the Parent is NOT Connected
            // - the parent is connected But its Path is Different

            BlockChainInfo blockChainParentInfo =  _getBlockChainInfo(tr, block.getPrevBlockHash().toString());
            if (blockChainParentInfo == null || blockChainParentInfo.getChainPathId() != blockChainInfo.getChainPathId())
                _removeChainPath(tr, blockChainInfo.getChainPathId());

            // Now, after disconecting this block, we need to check how many Connected Children its parent has left:
            // If it still has exactly ONE Children, then we can merge the Parent Path and its Children's Path, so they
            // become the same....
            if (blockChainParentInfo != null) {
                List<BlockChainInfo> connectedChildren =_getNextConnectedBlocks(tr, blockChainParentInfo.getBlockHash());
                if (connectedChildren.size() == 1) {
                    _removeChainPath(tr, connectedChildren.get(0).getChainPathId());
                    _propagateChainPathUnderBlock(tr, connectedChildren.get(0), connectedChildren.get(0).getChainPathId(), blockChainParentInfo.getChainPathId());
                }
            }

            // We update the tip of the chain (this block is not the tip anymore, if its already)
            _updateTipsChain(tr, null, blockHash);

            // We remove all the Chain Info from its Children...
            List<String> children = _getNextBlocks(tr, blockHash);
            children.forEach(h -> _disconnectBlock(tr, h));
        }
    }

    default void _initGenesisBlock(T tr, BlockHeader genesisBlock) {
        // We init the Info stored about the Paths in the Chain:
        _updateLastPathId(tr, 0);
        _createNewChainPath(tr, -1, genesisBlock.getHash().toString());

        // Now we insert (and connect) the Genesis Block:
        _saveBlock(tr, genesisBlock);
        _connectBlock(tr, genesisBlock, null); // No parent for this block
    }

    default void _publishState() {
            try {
                getLock().readLock().lock();
                //getLogger().trace("Publishing State...");
                ChainStateEvent event = ChainStateEvent.builder().state(getState()).build();
                getEventBus().publish(event);
                //getLogger().trace("State published");
            } catch (Exception e) {
                getLogger().error("ERROR at publishing State", e);
            } finally {
                 getLock().readLock().unlock();
            }
    }


    @Override
    default void _saveBlock(T tr, BlockHeader blockHeader) {
        String parentHashHex = blockHeader.getPrevBlockHash().toString();

        // we save te Block...:
        BlockStoreKeyValue.super._saveBlock(tr, blockHeader);

        // and its relation with its parent
        _addChildToBlock(tr, parentHashHex, blockHeader.getHash().toString());

        // We search for the ChainInfo of this block, to check if its already connected to the Chain:
        if (_getBlockChainInfo(tr, blockHeader.getHash().toString()) == null) {
            // If the Parent exists and it's also Connected, we connect this one too:
            BlockChainInfo parentChainInfo =  _getBlockChainInfo(tr, parentHashHex);
            if (parentChainInfo != null) {
                _connectBlock(tr, blockHeader, parentChainInfo);

                // If this is a fork, we trigger a Fork Event:
                List<String> parentChilds = _getNextBlocks(tr, blockHeader.getPrevBlockHash().toString());
                if (parentChilds != null && parentChilds.size() > 1) {
                    ChainForkEvent event = ChainForkEvent.builder()
                            .blockForkHash(blockHeader.getHash())
                            .parentForkHash(blockHeader.getPrevBlockHash())
                            .build();
                    getEventBus().publish(event);
                }
            }
        }
    }

    @Override
    default void _removeBlock(T tr, String blockHash) {
        // Basic check if the block exists:
        BlockHeader block = _getBlock(tr, blockHash);
        if (block == null) return;

        // We remove the relationship between this block and its parent:
        _removeChildFromBlock(tr, block.getPrevBlockHash().toString(), blockHash);
        _disconnectBlock(tr, blockHash);

        // We update the tip of the chain (the parent is now the tip of the chain, NUT ONLY if the Parent is ALSO
        // CONNECTED to the Chain)

        BlockChainInfo parentChainInfo = _getBlockChainInfo(tr, block.getPrevBlockHash().toString());
        if (parentChainInfo != null) {
            // If the parent has already other children then we do NOT do it, since that would mean that that parent
            // is already part of other chain
            List<String> parentChildren = _getNextBlocks(tr, block.getPrevBlockHash().toString());
            if (parentChildren == null || parentChildren.size() == 0)
                _updateTipsChain(tr, block.getPrevBlockHash().toString(), null);
        }

        // we remove the Block the usual way:
        BlockStoreKeyValue.super._removeBlock(tr, blockHash);
    }


    private void _updateLastPathId(T tr, int pathId) {
        byte[] key = fullKeyForChainPathsLast();
        save(tr, key, bytes(pathId));
    }

    private int _getLastPathId(T tr) {
        byte[] key = fullKeyForChainPathsLast();
        byte[] value = read(tr, key);
        int result = (value != null)? toInt(read(tr, key)) : 0;
        return result;
    }

    private ChainPathInfo _createNewChainPath(T tr, int parentPathId, String blockHash) {
        // We get the latest Path ID (if there is any) and we update it:
        int lastPathId = _getLastPathId(tr);
        _updateLastPathId(tr, ++lastPathId);

        // No we create a new record to store info about this Path:
        ChainPathInfo result = _saveChainPath(tr, lastPathId, parentPathId, blockHash);
        return result;
    }

    private ChainPathInfo _saveChainPath(T tr, int pathId, int parentId, String blockHash) {
        ChainPathInfo result = ChainPathInfo.builder()
                .id(pathId)
                .parent_id(parentId)
                .blockHash(blockHash)
                .build();
        byte[] key = fullKeyForChainPath(pathId);
        byte[] value = bytes(result);
        save(tr, key, value);
        getLogger().trace("PathInfo Saved [path id: " + pathId + ", parent path: " + parentId + "]");
        return result;
    }

    private void _removeChainPath(T tr, int pathId) {
        byte[] key = fullKeyForChainPath(pathId);
        remove(tr, key);
        getLogger().trace("PathInfo Removed [path id: " + pathId + "]");
    }

    private ChainPathInfo _getChainPathInfo(T tr, int pathId) {
        byte[] key = fullKeyForChainPath(pathId);
        byte[] value = read(tr, key);
        ChainPathInfo result = toChainPathInfo(value);
        return result;
    }


    private void _propagateChainPathUnderBlock(T tr, BlockChainInfo blockChainInfo, int pathIdToReplace, int newPathId) {

        // We Update the BlockInfoChain info for this block, reflecting the new Path Id its linked to, and also
        // does the same for all its children until it reaches a Block with more than 1 children....

        BlockChainInfo blockChainInfoToUpdate = blockChainInfo;

        while (true) {
            if (blockChainInfoToUpdate == null) break;
            if (blockChainInfoToUpdate.getChainPathId() != pathIdToReplace) break;
            getLogger().trace("Update Path for Block " + blockChainInfo.getBlockHash() + " (height: " + blockChainInfo.getHeight() + ") [ " + pathIdToReplace + " ->  " + newPathId + "]");

            // We update this Block Chain Info...
            blockChainInfoToUpdate = blockChainInfoToUpdate.toBuilder().chainPathId(newPathId).build();
            _saveBlockChainInfo(tr, blockChainInfoToUpdate);

            // We only keep going if this block ONLY has 1 CHILD:
            List<String> children = _getNextBlocks(tr, blockChainInfoToUpdate.getBlockHash());
            if (children.size() != 1) break;
            blockChainInfoToUpdate = _getBlockChainInfo(tr, children.get(0));
        } // while...

    }

    /*
     * High level Functions
     */

    @Override
    default BlockChainStoreState getState() {
        try {
            getLock().readLock().lock();
            List<ChainInfo> tipsChainInfo = getTipsChains().stream()
                    .map(h -> getBlockChainInfo(h).get())
                    .collect(Collectors.toList());
            return BlockChainStoreState.builder()
                    .tipsChains(tipsChainInfo)
                    .numBlocks(getNumBlocks())
                    .numTxs(getNumTxs())
                    .build();
        } finally {
            getLock().readLock().unlock();
        }

    }

    @Override
    default List<Sha256Wrapper> getTipsChains() {
        try {
            getLock().readLock().lock();
            List<Sha256Wrapper> result = new ArrayList<>();
            T tr = createTransaction();
            executeInTransaction(tr , () -> {
                List<String> tipsChain = _getChainTips(tr);
                result.addAll(tipsChain.stream().map(h -> Sha256Wrapper.wrap(h)).collect(Collectors.toList()));
            });
            return result;
        } finally {
            getLock().readLock().unlock();
        }
    }

    @Override
    default List<Sha256Wrapper> getTipsChains(Sha256Wrapper blockHash) {
        try {
            getLock().readLock().lock();
            List<Sha256Wrapper> result = new ArrayList<>();
            T tr = createTransaction();
            executeInTransaction(tr, () -> {
                // We only continue if the Block given is connected:
                BlockChainInfo blockChainInfo = _getBlockChainInfo(tr, blockHash.toString());
                if (blockChainInfo == null) return;

                // We loop over the current Tips of the Chains. For each one, we check if the Path they belong to, and
                // the Parent Paths of them. If the PAth of the block specified is one of those PAths, then we include
                // that tip in the result
                List<String> tipsChain = _getChainTips(tr);
                for (String tipHash : tipsChain) {
                    int chainPathId = _getBlockChainInfo(tr, tipHash).getChainPathId();
                    boolean blockHashIsPartOfPath = false;
                    do {
                        blockHashIsPartOfPath |= chainPathId == blockChainInfo.getChainPathId();
                        ChainPathInfo pathInfo = _getChainPathInfo(tr, chainPathId);
                        chainPathId = pathInfo.getParent_id();

                    } while (chainPathId != -1 && !blockHashIsPartOfPath);
                    if (blockHashIsPartOfPath) result.add(Sha256Wrapper.wrap(tipHash));
                }
            });
            return result;
        } finally {
            getLock().readLock().unlock();
        }
    }

    @Override
    default ChainInfo getFirstBlockInPath(Sha256Wrapper blockHash) {
        try {
            getLock().readLock().lock();
            AtomicReference<ChainInfo> result = new AtomicReference<>();
            T tr = createTransaction();
            executeInTransaction(tr, () -> {
                // We get the Path Info linked to this block...
                BlockChainInfo blockChainInfo = _getBlockChainInfo(tr, blockHash.toString());
                ChainPathInfo pathInfo = _getChainPathInfo(tr, blockChainInfo.getChainPathId());

                // Now we retrieve the blockChain info of the FIRST Block in this Path, and we convert it to
                // a ChainInfo Object...
                BlockChainInfo firstBlockInfo = _getBlockChainInfo(tr, pathInfo.getBlockHash());
                BlockHeader firstBlockHeader = _getBlock(tr, firstBlockInfo.getBlockHash());

                ChainInfo chainInfoResult = ChainInfo.builder()
                        .header(firstBlockHeader)
                        .chainWork(firstBlockInfo.getChainWork())
                        .height(firstBlockInfo.getHeight())
                        .sizeInBytes(firstBlockInfo.getTotalChainSize())
                        .build();

                result.set(chainInfoResult);
            });
            return result.get();

        } finally {
            getLock().readLock().unlock();
        }
    }

    @Override
    default void removeTipsChains() {
        try {
            getLock().writeLock().lock();
            T tr = createTransaction();
            executeInTransaction(tr, () -> {
                _saveChainTips(tr, HashesList.builder().build());
            });
        } finally {
            getLock().writeLock().unlock();
        }
    }

    @Override
    default Optional<ChainInfo> getBlockChainInfo(Sha256Wrapper blockHash) {
        try {
            getLock().readLock().lock();
            AtomicReference<ChainInfo> result = new AtomicReference<>();
            T tr = createTransaction();
            executeInTransaction(tr, () -> {
                BlockHeader block = _getBlock(tr, blockHash.toString());
                if (block == null) return;

                byte[] value = read(tr, fullKeyForBlockChainInfo(blockHash.toString()));
                BlockChainInfo blockChainInfo = toBlockChainInfo(value);
                ChainInfo chainInfoResult = ChainInfo.builder()
                        .header(block)
                        .chainWork(blockChainInfo.getChainWork())
                        .height(blockChainInfo.getHeight())
                        .sizeInBytes(blockChainInfo.getTotalChainSize())
                        .build();
                result.set(chainInfoResult);
            });
            return Optional.ofNullable(result.get());
        } finally {
            getLock().readLock().unlock();
        }
    }

    @Override
    default Optional<ChainInfo> getLongestChain() {
        try {
            getLock().readLock().lock();
            List<ChainInfo> tips = getState().getTipsChains();
            if (tips.size() == 0) return Optional.empty();
            if (tips.size() == 1) return Optional.of(tips.get(0));

            // There are more than one chain ( there is one or more FORKS). So we need to locate the Longest one:
            int maxHeight = tips.stream().mapToInt(c -> c.getHeight()).max().getAsInt();
            Optional<ChainInfo> result = tips.stream().filter(c -> c.getHeight() == maxHeight).findFirst();
            return result;
        } finally {
            getLock().readLock().unlock();
        }
    }

    @Override
    default Optional<Sha256Wrapper> getPrevBlock(Sha256Wrapper blockHash) {
        try {
            getLock().readLock().lock();
            Optional<Sha256Wrapper> result = getBlock(blockHash).map(b -> b.getPrevBlockHash());
            return result;
        } finally {
            getLock().readLock().unlock();
        }
    }

    @Override
    default List<Sha256Wrapper> getNextBlocks(Sha256Wrapper blockHash) {
        try {
            getLock().readLock().lock();
            List<Sha256Wrapper> result = new ArrayList<>();
            T tr = createTransaction();
            executeInTransaction(tr, () -> {
                List<String> children = _getNextBlocks(tr, blockHash.toString());
                result.addAll(children.stream().map(h -> Sha256Wrapper.wrap(h)).collect(Collectors.toList()));
            });
            return result;
        } finally {
            getLock().readLock().unlock();
        }
    }

    @Override
    default Iterable<Sha256Wrapper> getOrphanBlocks() {

        // We configure the parameters for creating an Iterator that loops over the ORPHAN Blocks:

        // The iterator will loop over that Keys that belong to the "blocks" folder and start with the preffix
        // used for storing blocks:
        byte[] startingWithKey = keyStartingWith(fullKey(fullKeyForBlocks(), KEY_PREFFIX_BLOCK));

        // The keyVerifier Function will check that each Key we loop over is a Valid Key: A Valid Key is a key that
        // references a Block that has NO parent block stored in the DB:

        BiPredicate<T, byte[]> keyVerifier = (tr, key) -> {
            String blockHash = extractBlockHashFromKey(key).get();
            if (blockHash.equals(getConfig().getGenesisBlock().getHash().toString())) return false;
            BlockHeader block = _getBlock(tr, blockHash);
            BlockHeader parent = _getBlock(tr, block.getPrevBlockHash().toString());
            return (parent == null);
        };

        // The "buildItemBy" is the function used to take a Key and return each Item of the Iterator. The iterator
        // will returns a series of BlockHeader, so this function will build a BlockHeader out of a Key:

        Function<E, Sha256Wrapper> buildItemBy = (E item) -> {
            byte[] key = keyFromItem(item);
            String blockHash = extractBlockHashFromKey(key).get();
            return Sha256Wrapper.wrap(blockHash);
        };

        // With everything set up, we create our Iterator and return it wrapped up in an Iterable:
        Iterator<Sha256Wrapper> iterator = getIterator(startingWithKey, null, keyVerifier, buildItemBy);

        Iterable<Sha256Wrapper> result = () -> iterator;
        return result;
    }

    @Override
    default void prune(Sha256Wrapper tipChainHash, boolean removeTxs) {
        getLogger().debug("Prunning chain tip #" + tipChainHash + " ...");
        try {
            getLock().writeLock().lock();
            List<Sha256Wrapper> tipsChains = getTipsChains();

            // First we check if this Hash really is a TIP of a chain:
            if (!tipsChains.contains(tipChainHash))
                throw new RuntimeException("The Hash specified for Prunning is NOT the Tip of any Chain.");


            // Now we move from the tip backwards until we find a Block that has MORE than one child (one being the Block
            // we are removing)

            boolean keepGoing = true;
            Sha256Wrapper hashBlockToRemove = tipChainHash;
            long numBlocksRemoved = 0;
            while (keepGoing) {

                // We find the Parent of this Block, and we stop if the parent has MORE than one child /that would mean that
                // that parent is the block right BEFORE the Fork), and we stop in that case
                Optional<Sha256Wrapper> parentHashOpt = getPrevBlock(hashBlockToRemove);
                if (parentHashOpt.isPresent()) {
                    List<Sha256Wrapper> children = getNextBlocks(parentHashOpt.get());
                    if (children.size() > 1) keepGoing = false;
                }

                // If enabled, we remove its TXs...
                if (removeTxs) removeBlockTxs(hashBlockToRemove);
                // We remove this Block
                removeBlock(hashBlockToRemove);

                numBlocksRemoved++;

                // In the next loop, we try to remove the Parent, if the parent exists
                if (parentHashOpt.isEmpty()) keepGoing = false;
                else hashBlockToRemove = parentHashOpt.get();
            } // while...

            getLogger().debug("chain tip #" + tipChainHash + " Pruned. " + numBlocksRemoved + " blocks removed.");

            // We trigger a Prune Event:
            ChainPruneEvent event = ChainPruneEvent.builder()
                    .tipForkHash(tipChainHash)
                    .parentForkHash(hashBlockToRemove)
                    .numBlocksPruned(numBlocksRemoved)
                    .build();
            getEventBus().publish(event);
        } finally {
            getLock().writeLock().unlock();
        }
    }

    // It performs an Automatic Prunning: It search for Fork Chains, and if they meet the criteria to be pruned, it
    // prunes them. Criteria to prune a Chain:
    // - it is NOT the longest Chain
    // - its Height is >= than "prunningHeightDifference"
    // - the difference of age between the block of the tip and the on in the tip of the longest chain is
    //   longer than "prunningAgeDifference"

    default void _automaticForkPrunning() {
            try {
                getLock().writeLock().lock();
                getLogger().info("Automatic Fork Pruning initiating...");
                // We only prune if there is more than one chain:
                List<Sha256Wrapper> tipsChain = getTipsChains();
                if (tipsChain != null && (tipsChain.size() > 1)) {
                    ChainInfo longestChain = getLongestChain().get();
                    List<Sha256Wrapper> tipsToPrune = getState().getTipsChains().stream()
                            .filter(c -> (!c.equals(longestChain))
                                    && ((longestChain.getHeight() - c.getHeight()) >= getConfig().getForkPrunningHeightDifference()))
                            .map(c -> c.getHeader().getHash())
                            .collect(Collectors.toList());
                    tipsToPrune.forEach(c -> prune(c, getConfig().isForkPrunningIncludeTxs()));
                }
                getLogger().info("Automatic Fork Pruning finished.");
            } finally {
                getLock().writeLock().unlock();
            }
    }

    default void _automaticOrphanPrunning() {
            try {
                getLock().writeLock().lock();
                getLogger().info("Automatic Orphan Pruning initiating...");
                int numBlocksRemoved = 0;
                // we get the list of Orphans, and we remove them if they are old" enough:
                Iterator<Sha256Wrapper> orphansIt = getOrphanBlocks().iterator();
                while (orphansIt.hasNext()) {
                    Sha256Wrapper blockHash = orphansIt.next();
                    Optional<BlockHeader> blockHeaderOpt = getBlock(blockHash);
                    if (blockHeaderOpt.isPresent()) {
                        Instant blockTime = Instant.ofEpochSecond(blockHeaderOpt.get().getTime());
                        if (Duration.between(blockTime, Instant.now()).compareTo(getConfig().getOrphanPrunningBlockAge()) > 0) {
                            removeBlock(blockHash);
                            numBlocksRemoved++;
                        }
                    }
                } // while...
                getLogger().info("Automatic Orphan Prnning finished. " + numBlocksRemoved + " orphan Blocks Removed");
            } finally {
                getLock().writeLock().unlock();
            }
        }
}
