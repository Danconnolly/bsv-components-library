package io.bitcoinsv.jcl.store.blockStore


import io.bitcoinsv.bitcoinjsv.bitcoin.bean.base.TxBean
import io.bitcoinsv.jcl.tools.common.TestingUtils
import io.bitcoinsv.bitcoinjsv.bitcoin.api.base.Tx
import io.bitcoinsv.bitcoinjsv.core.Sha256Hash

import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Collectors

/**
 * Testing class for Basic Scenarios with Txs (inserting, removing, etc).
 *
 * SO this class can NOT be tested itself, it needs to be extended. An extending class must implement the "getInstance"
 * method, which returns a concrete implementation of the BlockStore interface (like a LevelDB or FoundationDB
 * Implementation).
 *
 * Once that method is implemented, the extending class can be tested without any other additions, since running the
 * extending class will automatically trigger the tests defined in THIS class.
 */
abstract class BlockStoreTxsSpecBase extends BlockStoreSpecBase {

    /**
     * We test that TXs are properly saved and removed into the DB and the related Events are properly triggered.
     */
    def "testing saving/removing Txs"() {
        given:
            println(" - Connecting to the DB...")
            BlockStore db = getInstance("BSV-Main", false, true)
            // We keep track of the Events triggered:
            AtomicInteger numTxsSavedEvents = new AtomicInteger()
            AtomicInteger numTxsRemovedEvents = new AtomicInteger();
            db.EVENTS().TXS_SAVED.forEach({e -> numTxsSavedEvents.incrementAndGet()})
            db.EVENTS().TXS_REMOVED.forEach({e -> numTxsRemovedEvents.incrementAndGet()})

        when:
            db.start()
            //TestingUtils.clearDB(blockStore.db)

            // We define 3 Txs:
            Tx tx1 = TestingUtils.buildTx()
            Tx tx2 = TestingUtils.buildTx()
            Tx tx3 = TestingUtils.buildTx()

            // We save 1 individual Txs:
            long numTxsBeforeAll = db.getNumTxs()
            boolean isTx1FoundBeforeInserting = db.containsTx(tx1.getHash())
            println(" - Saving Tx " + tx1.getHash().toString() + "...")
            db.saveTx(tx1)
            long numTxsAfter1Tx = db.getNumTxs()
            boolean isTx1FoundAfterInserting = db.containsTx(tx1.getHash())

            // We save the remaining 2 Txs in a single batch:
            println(" - Saving a Batch of 2 Txs:")
            println(" - tx " + tx2.getHash().toString())
            println(" - tx " + tx3.getHash().toString())

            db.saveTxs(Arrays.asList(tx2, tx3))
            long numTxsAfter3Txs = db.getNumTxs()

            // We check the DB Content in the console...
            db.printKeys()

            // We remove one Tx individually:
            println(" - Removing Tx " + tx2.getHash().toString() + "...")
            db.removeTx(tx2.getHash())
            long numTxsAfterRemove1Tx = db.getNumTxs()

            // The 2 remaining Txs are removed in a single Batch:
            println(" - Removing a Batch of 2 Txs:")
            println(" - tx " + tx2.getHash().toString())
            println(" - tx " + tx3.getHash().toString())
            db.removeTxs(Arrays.asList(tx1.getHash(), tx3.getHash()))
            long numTxsAfterRemove3Tx = db.getNumTxs()

            // We check the DB Content in the console...
            db.printKeys()

        then:
            numTxsBeforeAll == 0
            numTxsAfter1Tx == 1
            !isTx1FoundBeforeInserting
            isTx1FoundAfterInserting
            numTxsAfter3Txs == 3
            numTxsSavedEvents.get() == 2
            numTxsSavedEvents.get() == 2
            numTxsAfterRemove1Tx == 2
            numTxsAfterRemove3Tx == 0
        cleanup:
            println(" - Cleanup...")
            db.removeTxs(Arrays.asList(tx1.getHash(), tx2.getHash(), tx3.getHash()))
            db.printKeys()
            db.clear()
            db.stop()
            println(" - Test Done.")
    }

    /**
     * We test that we save several Txs, some of them spending the outputs of others, and then we check that we can
     * recover this information.
     */
    def "testing Txs Needed"() {
        final int NUM_TXS = 3
        given:
            println(" - Connecting to the DB...")
            BlockStore db = getInstance("BSV-Main", false, true)
        when:
            db.start()
            //TestingUtils.clearDB(blockStore.db)
            // We save several Txs: after the First, each one is using an output generated by the previous Txs...
            List<Tx> txs = new ArrayList<>()
            for (int i = 0; i < NUM_TXS; i++) {
                String parentTxHash = (i == 0) ? null : txs.get(i - 1).getHash().toString();
                txs.add(TestingUtils.buildTx(parentTxHash))
            }

            // We save all the Txs
            println(" - Saving " + NUM_TXS + "...")
            txs.forEach({ tx -> println(" - tx " + tx.getHash().toString() + " saved.")})
            db.saveTxs(txs)

            // We check the DB Content in the console...
            db.printKeys()

            // Now we recover each of them, checking that the info about the Txs Needed is correct for each one...
            Boolean OK = true
            List<Sha256Hash> txHashes = txs.stream().map({ tx -> tx.getHash()}).collect(Collectors.toList())
            for (int i = 0; i < NUM_TXS; i++) {
                Tx tx = db.getTx(txHashes.get(i)).get()
                List<Sha256Hash> txsNeeded = db.getPreviousTxs(tx.getHash())
                if (i > 0) {
                    OK &= txsNeeded.size() == 1 &&  txsNeeded.get(0).equals(txHashes.get(i - 1))
                }
            }
        then:
            OK
        cleanup:
            println(" - Cleanup...")
            db.removeTxs(txs.stream().map({tx -> tx.getHash()}).collect(Collectors.toList()))
            db.printKeys()
            db.clear()
            db.stop()
            println(" - Test Done.")
    }

    /**
     * We test that the 'saveTxsIfNotExist' methods works fine. It inserts only those Txs that do NOT exist yet, and
     * return them
     */
    def "testing inserting Txs only if Not Exist"() {
        given:
            println(" - Connecting to the DB...")
            BlockStore db = getInstance("BSV-Main", false, true)
        when:
            db.start()

            // We insert a couple of Txs in the DB...
            Tx txA = TestingUtils.buildTx()
            Tx txB = TestingUtils.buildTx()
            db.saveTx(txA)
            db.saveTx(txB)

            int numTxsInitial = db.getNumTxs()

            // Now we insert 3 Tx, one of them is already there:
            Tx txC = TestingUtils.buildTx()
            Tx txD = TestingUtils.buildTx()
            List<Tx> txsInserted = db.saveTxsIfNotExist(Arrays.asList(txC, txB, txD))

            int numTxsFinal = db.getNumTxs()
        then:
            numTxsInitial == 2
            numTxsFinal == 4
            txsInserted.size() == 2
            txsInserted.contains(txC)
            txsInserted.contains(txD)
        cleanup:
            println(" - Cleanup...")
            db.removeTxs(txsInserted.stream().map({tx -> tx.hash}).collect(Collectors.toList()))
            db.printKeys()
            db.clear()
            db.stop()
            println(" - Test Done.")
    }

    /**
     * We test that the 'saveTxsIfNotExistAsync' methods works fine. It inserts only those Txs that do NOT exist yet,
     * and return them. This method works asynchronously, so we have to wait until the result is available. In this
     * tests we are going to launch several calls to this method.
     */
    def "testing inserting Txs only if Not Exist Async"() {
        given:
            final int NUM_CALLS = 3
            final int NUM_TAXS_EACH_CALL = 2
            println(" - Connecting to the DB...")
            BlockStore db = getInstance("BSV-Main", false, true)
        when:
            db.start()

            // We loop (NUM_CALLS) times, creating a set of (NUM_TXS_EACH_CALL) for each iteration. On each
            // iteration, we insert those Txs but ONE of those Txs have been already inserted:

            List<CompletableFuture<List<Tx>>> methodFutureResults = new ArrayList<>()
            List<Tx> totalTxsInserted = new ArrayList<>()
            Tx txRepeated = null;
            for (int i = 0; i < NUM_CALLS; i++) {
                List<Tx> txsToInsert = new ArrayList<>();
                for (int b = 0; b < NUM_TAXS_EACH_CALL; b++) {
                    txsToInsert.add(TestingUtils.buildTx())
                }
                if (txRepeated != null) {
                    txsToInsert.add(txRepeated)
                }
                CompletableFuture<List<Tx>> txsInsertedFuture = db.saveTxsIfNotExistAsync(txsToInsert)
                methodFutureResults.add(txsInsertedFuture)
                println("iteration " + i + " :: Trying to save " + txsToInsert.size() + " txs... ")
                txRepeated = txsToInsert.get(0)
            }

            // Now we iterate over all the Results...
            for (int i = 0; i < NUM_CALLS; i++) {
                CompletableFuture<List<Tx>> txsInsertedF = methodFutureResults.get(i);
                totalTxsInserted.addAll(txsInsertedF.get())
                println("iteration " + i + " :: " + txsInsertedF.get().size() + " txs saved. ")
            }

            println(totalTxsInserted.size() + " txs saved in total")

            int numTxsInserted = db.getNumTxs()

        then:
            numTxsInserted == NUM_CALLS * NUM_TAXS_EACH_CALL
            numTxsInserted == totalTxsInserted.size()

        cleanup:
            println(" - Cleanup...")
            db.removeTxs(totalTxsInserted.stream().map({tx -> tx.hash}).collect(Collectors.toList()))
            db.printKeys()
            db.clear()
            db.stop()
        println(" - Test Done.")
    }

    def "testing saving of big tx"() {
        given:
        println(" - Connecting to the DB...")
        BlockStore db = getInstance("BSV-Main", false, true)
        int blockSizeBytes = 50_000_000

        when:
        db.start()
        //TestingUtils.clearDB(blockStore.db)

        // We define 3 Txs:
        Tx tx1 = TestingUtils.buildTx()

        tx1.makeMutable()

        tx1.getOutputs().get(0).setScriptBytes(new byte[blockSizeBytes])

        // We save 1 individual Txs:
        long time = System.currentTimeMillis();
        println(" - Saving Tx " + tx1.getHash().toString() + "...")
        db.saveTx(tx1)
        time = System.currentTimeMillis() - time;
        println(" - Tx " + tx1.getHash().toString() + "saved in: " + time + " ms")

        then:
        db.getTx(tx1.hash).get().getHash() == tx1.hash
        db.getNumTxs() == 1

        cleanup:
        println(" - Cleanup...")
        db.removeTx(tx1.getHash())
        db.printKeys()
        db.clear()
        db.stop()
        println(" - Test Done.")
    }

    def "testing save tx volume"() {
        given:
        println(" - Connecting to the DB...")
        BlockStore db = getInstance("BSV-Main", false, true)
        int totalTxs = 100_000;
        int txScriptSizeBytes = 0;

        when:
        db.start()

        List<Tx> txs = new ArrayList<>();
        byte[] script = new byte[txScriptSizeBytes]
        for(int i = 0; i < totalTxs; i++) {
            Tx tx = TestingUtils.buildTx()

            tx.makeMutable()
            tx.getOutputs().get(0).setScriptBytes(script)
            tx.getHash();
            tx.makeImmutable()

            TxBean newTx = new TxBean(tx.serialize());
            newTx.getHash()

            txs.add(newTx);
        }

        long time = System.currentTimeMillis();
        println(" - Saving: " + txs.size() + " Txs...")
        db.saveTxs(txs)
        time = System.currentTimeMillis() - time;
        println(" - Txs saved in: " + time + " ms")

        then:
        db.getNumTxs() == txs.size()
        db.getTx(txs.get(0).getHash()).get() == txs.get(0)

        cleanup:
        println(" - Cleanup...")
        db.clear()
        db.stop()
        println(" - Test Done.")
    }

    def "testing read tx volume"() {
        given:
        println(" - Connecting to the DB...")
        BlockStore db = getInstance("BSV-Main", false, true)
        int totalTxs = 100000;
        int txScriptSizeBytes = 500;

        when:
        db.start()

        List<Tx> txs = new ArrayList<>();
        byte[] script = new byte[txScriptSizeBytes]
        for(int i = 0; i < totalTxs; i++) {
            Tx tx = TestingUtils.buildTx()

            tx.makeMutable()
            tx.getOutputs().get(0).setScriptBytes(script)
            tx.getHash();
            tx.makeImmutable()

            TxBean newTx = new TxBean(tx.serialize());
            newTx.getHash()

            txs.add(newTx);
        }

        long time = System.currentTimeMillis();
        println(" - Saving: " + txs.size() + " Txs...")
        db.saveBlockTxs(Sha256Hash.ZERO_HASH, txs)
        time = System.currentTimeMillis() - time;
        println(" - Txs saved in: " + time + " ms")

        time = System.currentTimeMillis();
        println(" - Reading: " + txs.size() + " Txs...")
        List<Sha256Hash> txHashesSaved = db.getBlockTxs(Sha256Hash.ZERO_HASH).toList();
        time = System.currentTimeMillis() - time;
        println(" - " + txHashesSaved.size() + " Txs read in: " + time + " ms")

        then:
        db.getNumTxs() == txs.size()
        db.getTx(txs.get(0).getHash()).get() == txs.get(0)
        db.getBlockHashLinkedToTx(txs.get(0).getHash()).get(0) == Sha256Hash.ZERO_HASH
        txs.stream().map({t -> t.getHash()}).collect(Collectors.toList()) == txHashesSaved

        cleanup:
        println(" - Cleanup...")
        db.clear()
        db.stop()
        println(" - Test Done.")
    }
}
