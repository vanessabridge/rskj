/*
 * This file is part of RskJ
 * Copyright (C) 2017 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.rsk.peg;

import co.rsk.blockchain.utils.BlockGenerator;
import co.rsk.blockchain.utils.BlockchainBuilder;
import co.rsk.crypto.Sha3Hash;
import co.rsk.peg.simples.SimpleRskTransaction;
import com.google.common.collect.Lists;
import co.rsk.config.BridgeConstants;
import co.rsk.config.BridgeRegTestConstants;
import co.rsk.peg.simples.SimpleBlockChain;
import co.rsk.peg.simples.SimpleBridgeStorageProvider;
import co.rsk.peg.simples.SimpleWallet;
import org.spongycastle.util.encoders.Hex;
import co.rsk.bitcoinj.core.*;
import co.rsk.bitcoinj.core.BtcTransaction;
import co.rsk.bitcoinj.crypto.TransactionSignature;
import co.rsk.bitcoinj.script.Script;
import co.rsk.bitcoinj.script.ScriptBuilder;
import co.rsk.bitcoinj.script.ScriptChunk;
import co.rsk.bitcoinj.store.BtcBlockStore;
import co.rsk.bitcoinj.store.BlockStoreException;
import org.ethereum.config.BlockchainNetConfig;
import org.ethereum.config.SystemProperties;
import org.ethereum.config.blockchain.RegTestConfig;

import org.ethereum.config.net.TestNetConfig;
import org.ethereum.core.*;
import org.ethereum.core.Block;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.db.ReceiptStore;
import co.rsk.db.RepositoryImpl;
import org.ethereum.db.TransactionInfo;
import org.ethereum.vm.PrecompiledContracts;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;
import org.spongycastle.crypto.params.ECPrivateKeyParameters;
import org.spongycastle.crypto.signers.ECDSASigner;

import java.io.*;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;

/**
 * Created by ajlopez on 6/9/2016.
 */
public class BridgeSupportTest {
    private static final String contractAddress = PrecompiledContracts.BRIDGE_ADDR;

    private static BlockchainNetConfig blockchainNetConfigOriginal;
    private static BridgeConstants bridgeConstants;
    private static NetworkParameters btcParams;

    private static final String TO_ADDRESS = "00000000000000000006";
    private static final BigInteger DUST_AMOUNT = new BigInteger("1");
    private static final BigInteger AMOUNT = new BigInteger("1000000000000000000");
    private static final BigInteger NONCE = new BigInteger("0");
    private static final BigInteger GAS_PRICE = new BigInteger("100");
    private static final BigInteger GAS_LIMIT = new BigInteger("1000");
    private static final String DATA = "80af2871";

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        blockchainNetConfigOriginal = SystemProperties.CONFIG.getBlockchainConfig();
        SystemProperties.CONFIG.setBlockchainConfig(new RegTestConfig());
        bridgeConstants = SystemProperties.CONFIG.getBlockchainConfig().getCommonConstants().getBridgeConstants();
        btcParams = bridgeConstants.getBtcParams();
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        SystemProperties.CONFIG.setBlockchainConfig(blockchainNetConfigOriginal);
    }

    @Test
    public void testInitialChainHeadWithoutBtcCheckpoints() throws Exception {
        BlockchainNetConfig blockchainNetConfigOriginal = SystemProperties.CONFIG.getBlockchainConfig();
        SystemProperties.CONFIG.setBlockchainConfig(new RegTestConfig());
        NetworkParameters _networkParameters = SystemProperties.CONFIG.getBlockchainConfig().getCommonConstants().getBridgeConstants().getBtcParams();

        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);
        BridgeSupport bridgeSupport = new BridgeSupport(track, PrecompiledContracts.BRIDGE_ADDR, provider, null, null, null);
        Assert.assertEquals(0, bridgeSupport.getBtcBlockStore().getChainHead().getHeight());
        Assert.assertEquals(_networkParameters.getGenesisBlock(), bridgeSupport.getBtcBlockStore().getChainHead().getHeader());

        SystemProperties.CONFIG.setBlockchainConfig(blockchainNetConfigOriginal);
    }



    @Test
    public void testInitialChainHeadWithBtcCheckpoints() throws Exception {
        BlockchainNetConfig blockchainNetConfigOriginal = SystemProperties.CONFIG.getBlockchainConfig();
        SystemProperties.CONFIG.setBlockchainConfig(new TestNetConfig());

        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);
        BridgeSupport bridgeSupport = new BridgeSupport(track, PrecompiledContracts.BRIDGE_ADDR, provider, null, null, null);
        Assert.assertEquals(1116864, bridgeSupport.getBtcBlockStore().getChainHead().getHeight());

        SystemProperties.CONFIG.setBlockchainConfig(blockchainNetConfigOriginal);
    }

    @Test
    public void testGetBtcBlockchainBlockLocatorWithoutBtcCheckpoints() throws Exception {
        BlockchainNetConfig blockchainNetConfigOriginal = SystemProperties.CONFIG.getBlockchainConfig();
        SystemProperties.CONFIG.setBlockchainConfig(new RegTestConfig());
        NetworkParameters _networkParameters = SystemProperties.CONFIG.getBlockchainConfig().getCommonConstants().getBridgeConstants().getBtcParams();

        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);
        BridgeSupport bridgeSupport = new BridgeSupport(track, PrecompiledContracts.BRIDGE_ADDR, provider, null, null, null);
        Assert.assertEquals(0, bridgeSupport.getBtcBlockStore().getChainHead().getHeight());
        Assert.assertEquals(_networkParameters.getGenesisBlock(), bridgeSupport.getBtcBlockStore().getChainHead().getHeader());

        List<Sha256Hash> locator = bridgeSupport.getBtcBlockchainBlockLocator();
        Assert.assertEquals(1, locator.size());
        Assert.assertEquals(_networkParameters.getGenesisBlock().getHash(), locator.get(0));

        List<BtcBlock> blocks = createBtcBlocks(_networkParameters, _networkParameters.getGenesisBlock(), 10);
        bridgeSupport.receiveHeaders(blocks.toArray(new BtcBlock[]{}));
        locator = bridgeSupport.getBtcBlockchainBlockLocator();
        Assert.assertEquals(6, locator.size());
        Assert.assertEquals(blocks.get(9).getHash(), locator.get(0));
        Assert.assertEquals(blocks.get(8).getHash(), locator.get(1));
        Assert.assertEquals(blocks.get(7).getHash(), locator.get(2));
        Assert.assertEquals(blocks.get(5).getHash(), locator.get(3));
        Assert.assertEquals(blocks.get(1).getHash(), locator.get(4));
        Assert.assertEquals(_networkParameters.getGenesisBlock().getHash(), locator.get(5));

        SystemProperties.CONFIG.setBlockchainConfig(blockchainNetConfigOriginal);
    }


    @Test
    public void testGetBtcBlockchainBlockLocatorWithBtcCheckpoints() throws Exception {
        BlockchainNetConfig blockchainNetConfigOriginal = SystemProperties.CONFIG.getBlockchainConfig();
        SystemProperties.CONFIG.setBlockchainConfig(new RegTestConfig());
        NetworkParameters _networkParameters = SystemProperties.CONFIG.getBlockchainConfig().getCommonConstants().getBridgeConstants().getBtcParams();

        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);
        List<BtcBlock> checkpoints = createBtcBlocks(_networkParameters, _networkParameters.getGenesisBlock(), 10);
        BridgeSupport bridgeSupport = new BridgeSupport(track, PrecompiledContracts.BRIDGE_ADDR, provider, null, null, null) {
            @Override
            InputStream getCheckPoints() {
                return getCheckpoints(_networkParameters, checkpoints);
            }
        };
        Assert.assertEquals(10, bridgeSupport.getBtcBlockStore().getChainHead().getHeight());
        Assert.assertEquals(checkpoints.get(9), bridgeSupport.getBtcBlockStore().getChainHead().getHeader());

        List<Sha256Hash> locator = bridgeSupport.getBtcBlockchainBlockLocator();
        Assert.assertEquals(1, locator.size());
        Assert.assertEquals(checkpoints.get(9).getHash(), locator.get(0));

        List<BtcBlock> blocks = createBtcBlocks(_networkParameters, checkpoints.get(9), 10);
        bridgeSupport.receiveHeaders(blocks.toArray(new BtcBlock[]{}));
        locator = bridgeSupport.getBtcBlockchainBlockLocator();
        Assert.assertEquals(6, locator.size());
        Assert.assertEquals(blocks.get(9).getHash(), locator.get(0));
        Assert.assertEquals(blocks.get(8).getHash(), locator.get(1));
        Assert.assertEquals(blocks.get(7).getHash(), locator.get(2));
        Assert.assertEquals(blocks.get(5).getHash(), locator.get(3));
        Assert.assertEquals(blocks.get(1).getHash(), locator.get(4));
        Assert.assertEquals(checkpoints.get(9).getHash(), locator.get(5));

        SystemProperties.CONFIG.setBlockchainConfig(blockchainNetConfigOriginal);
    }



    private List<BtcBlock> createBtcBlocks(NetworkParameters _networkParameters, BtcBlock parent, int numberOfBlocksToCreate) {
        List<BtcBlock> list = new ArrayList<>();
        for (int i = 0; i < numberOfBlocksToCreate; i++) {
            BtcBlock block = new BtcBlock(_networkParameters, 2l, parent.getHash(), Sha256Hash.ZERO_HASH, parent.getTimeSeconds()+1, parent.getDifficultyTarget(), 0, new ArrayList<BtcTransaction>());
            block.solve();
            list.add(block);
            parent = block;
        }
        return list;
    }


    private InputStream getCheckpoints(NetworkParameters _networkParameters, List<BtcBlock> checkpoints) {
        try {
            ByteArrayOutputStream baOutputStream = new ByteArrayOutputStream();
            MessageDigest digest = Sha256Hash.newDigest();
            final DigestOutputStream digestOutputStream = new DigestOutputStream(baOutputStream, digest);
            digestOutputStream.on(false);
            final DataOutputStream dataOutputStream = new DataOutputStream(digestOutputStream);
            StoredBlock storedBlock = new StoredBlock(_networkParameters.getGenesisBlock(), _networkParameters.getGenesisBlock().getWork(), 0);
            try {
                dataOutputStream.writeBytes("CHECKPOINTS 1");
                dataOutputStream.writeInt(0);  // Number of signatures to read. Do this later.
                digestOutputStream.on(true);
                dataOutputStream.writeInt(checkpoints.size());
                ByteBuffer buffer = ByteBuffer.allocate(StoredBlock.COMPACT_SERIALIZED_SIZE);
                for (BtcBlock block : checkpoints) {
                    storedBlock = storedBlock.build(block);
                    storedBlock.serializeCompact(buffer);
                    dataOutputStream.write(buffer.array());
                    buffer.position(0);
                }
            }
            finally {
                dataOutputStream.close();
                digestOutputStream.close();
                baOutputStream.close();
            }
            return new ByteArrayInputStream(baOutputStream.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    @Test
    public void callUpdateCollectionsFundsEnoughForJustTheSmallerTx() throws IOException, BlockStoreException {
        BtcTransaction tx1 = new BtcTransaction(btcParams);
        tx1.addOutput(Coin.valueOf(30,0), new BtcECKey().toAddress(btcParams));
        BtcTransaction tx2 = new BtcTransaction(btcParams);
        tx2.addOutput(Coin.valueOf(20,0), new BtcECKey().toAddress(btcParams));
        BtcTransaction tx3 = new BtcTransaction(btcParams);
        tx3.addOutput(Coin.valueOf(10,0), new BtcECKey().toAddress(btcParams));
        Sha3Hash hash1 = PegTestUtils.createHash3();
        Sha3Hash hash2 = PegTestUtils.createHash3();
        Sha3Hash hash3 = PegTestUtils.createHash3();

        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);

        provider0.getRskTxsWaitingForConfirmations().put(hash1, tx1);
        provider0.getRskTxsWaitingForConfirmations().put(hash2, tx2);
        provider0.getRskTxsWaitingForConfirmations().put(hash3, tx3);
        provider0.getBtcUTXOs().add(new UTXO(PegTestUtils.createHash(), 1, Coin.valueOf(12,0), 0, false, ScriptBuilder.createOutputScript(bridgeConstants.getFederationAddress())));

        provider0.save();

        track.commit();

        track = repository.startTracking();

        List<Block> blocks = BlockGenerator.getSimpleBlockChain(BlockGenerator.getGenesisBlock(), 10);
        TransactionReceipt receipt1 = new TransactionReceipt();
        org.ethereum.core.Transaction rskTx1 = new SimpleRskTransaction(hash1.getBytes());
        receipt1.setTransaction(rskTx1);
        TransactionInfo ti1 = new TransactionInfo(receipt1, blocks.get(1).getHash(), 0);
        TransactionReceipt receipt2 = new TransactionReceipt();
        org.ethereum.core.Transaction rskTx2 = new SimpleRskTransaction(hash2.getBytes());
        receipt2.setTransaction(rskTx2);
        TransactionInfo ti2 = new TransactionInfo(receipt2, blocks.get(1).getHash(), 1);
        TransactionReceipt receipt3 = new TransactionReceipt();
        org.ethereum.core.Transaction rskTx3 = new SimpleRskTransaction(hash3.getBytes());
        receipt3.setTransaction(rskTx3);
        TransactionInfo ti3 = new TransactionInfo(receipt3, blocks.get(1).getHash(), 2);

        List<TransactionInfo> tis = Lists.newArrayList(ti1, ti2, ti3);
        blocks.get(1).getTransactionsList().add(rskTx1);
        blocks.get(1).getTransactionsList().add(rskTx2);
        blocks.get(1).getTransactionsList().add(rskTx3);
        blocks.get(1).flushRLP();

        BlockchainBuilder builder = new BlockchainBuilder();

        Blockchain blockchain = builder.setTesting(true).setRsk(true).setTransactionInfos(tis).setGenesis(BlockGenerator.getGenesisBlock()).build();

        for (int k = 0; k < blocks.size(); k++)
            blockchain.getBlockStore().saveBlock(blocks.get(k), BigInteger.ONE, true);

        org.ethereum.core.Block rskCurrentBlock = blocks.get(9);
        ReceiptStore rskReceiptStore = blockchain.getReceiptStore();
        org.ethereum.db.BlockStore rskBlockStore = blockchain.getBlockStore();

        BridgeSupport bridgeSupport = new BridgeSupport(track, PrecompiledContracts.BRIDGE_ADDR, rskCurrentBlock, rskReceiptStore, rskBlockStore);

        bridgeSupport.updateCollections();

        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR);

        Assert.assertEquals(2, provider.getRskTxsWaitingForConfirmations().size());
        Assert.assertEquals(1, provider.getRskTxsWaitingForSignatures().size());
        // Check value sent to user is 10 BTC minus fee
        Assert.assertEquals(Coin.valueOf(999962800l), provider.getRskTxsWaitingForSignatures().values().iterator().next().getOutput(0).getValue());
    }

    @Test
    public void callUpdateCollectionsThrowsCouldNotAdjustDownwards() throws IOException, BlockStoreException {
        BtcTransaction tx1 = new BtcTransaction(btcParams);
        tx1.addOutput(Coin.valueOf(37500), new BtcECKey().toAddress(btcParams));
        Sha3Hash hash1 = PegTestUtils.createHash3();

        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);

        provider0.getRskTxsWaitingForConfirmations().put(hash1, tx1);
        provider0.getBtcUTXOs().add(new UTXO(PegTestUtils.createHash(), 1, Coin.valueOf(1000000), 0, false, ScriptBuilder.createOutputScript(bridgeConstants.getFederationAddress())));

        provider0.save();

        track.commit();

        track = repository.startTracking();

        List<Block> blocks = BlockGenerator.getSimpleBlockChain(BlockGenerator.getGenesisBlock(), 10);
        TransactionReceipt receipt1 = new TransactionReceipt();
        org.ethereum.core.Transaction rskTx1 = new SimpleRskTransaction(hash1.getBytes());
        receipt1.setTransaction(rskTx1);
        TransactionInfo ti1 = new TransactionInfo(receipt1, blocks.get(1).getHash(), 0);

        List<TransactionInfo> tis = Lists.newArrayList(ti1);
        BlockchainBuilder builder = new BlockchainBuilder();

        Blockchain blockchain = builder.setTesting(true).setRsk(true).setTransactionInfos(tis).build();

        blocks.get(1).getTransactionsList().add(rskTx1);
        blocks.get(1).flushRLP();

        for (int k = 0; k < blocks.size(); k++)
            blockchain.getBlockStore().saveBlock(blocks.get(k), BigInteger.ONE, true);

        org.ethereum.core.Block rskCurrentBlock = blocks.get(9);
        ReceiptStore rskReceiptStore = blockchain.getReceiptStore();
        org.ethereum.db.BlockStore rskBlockStore = blockchain.getBlockStore();

        BridgeSupport bridgeSupport = new BridgeSupport(track, PrecompiledContracts.BRIDGE_ADDR, rskCurrentBlock, rskReceiptStore, rskBlockStore);

        bridgeSupport.updateCollections();

        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR);

        Assert.assertEquals(1, provider.getRskTxsWaitingForConfirmations().size());
        Assert.assertEquals(0, provider.getRskTxsWaitingForSignatures().size());
    }

    @Test
    public void callUpdateCollectionsThrowsExceededMaxTransactionSize() throws IOException, BlockStoreException {
        BtcTransaction tx1 = new BtcTransaction(btcParams);
        tx1.addOutput(Coin.COIN.multiply(7), new BtcECKey().toAddress(btcParams));
        Sha3Hash hash1 = PegTestUtils.createHash3();

        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);

        provider0.getRskTxsWaitingForConfirmations().put(hash1, tx1);
        for (int i = 0; i < 2000; i++) {
            provider0.getBtcUTXOs().add(new UTXO(PegTestUtils.createHash(), 1, Coin.CENT, 0, false, ScriptBuilder.createOutputScript(bridgeConstants.getFederationAddress())));
        }

        provider0.save();

        track.commit();

        track = repository.startTracking();

        List<Block> blocks = BlockGenerator.getSimpleBlockChain(BlockGenerator.getGenesisBlock(), 10);
        TransactionReceipt receipt1 = new TransactionReceipt();
        org.ethereum.core.Transaction rskTx1 = new SimpleRskTransaction(hash1.getBytes());
        receipt1.setTransaction(rskTx1);
        TransactionInfo ti1 = new TransactionInfo(receipt1, blocks.get(1).getHash(), 0);

        List<TransactionInfo> tis = Lists.newArrayList(ti1);
        BlockchainBuilder builder = new BlockchainBuilder();

        Blockchain blockchain = builder.setTesting(true).setRsk(true).setTransactionInfos(tis).build();
        blocks.get(1).getTransactionsList().add(rskTx1);
        blocks.get(1).flushRLP();
        for (int k = 0; k < blocks.size(); k++)
            blockchain.getBlockStore().saveBlock(blocks.get(k), BigInteger.ONE, true);

        org.ethereum.core.Block rskCurrentBlock = blocks.get(9);
        ReceiptStore rskReceiptStore = blockchain.getReceiptStore();
        org.ethereum.db.BlockStore rskBlockStore = blockchain.getBlockStore();

        BridgeSupport bridgeSupport = new BridgeSupport(track, PrecompiledContracts.BRIDGE_ADDR, rskCurrentBlock, rskReceiptStore, rskBlockStore);

        bridgeSupport.updateCollections();

        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR);

        Assert.assertEquals(1, provider.getRskTxsWaitingForConfirmations().size());
        Assert.assertEquals(0, provider.getRskTxsWaitingForSignatures().size());
    }

    @Test
    public void callUpdateCollectionsChangeGetsOutOfDust() throws IOException, BlockStoreException {
        BtcTransaction tx1 = new BtcTransaction(btcParams);
        tx1.addOutput(Coin.COIN, new BtcECKey().toAddress(btcParams));
        Sha3Hash hash1 = PegTestUtils.createHash3();

        Map<byte[], BigInteger> preMineMap = new HashMap<byte[], BigInteger>();
        preMineMap.put(Hex.decode(PrecompiledContracts.BRIDGE_ADDR), Denomination.satoshisToWeis(BigInteger.valueOf(21000000)));

        Genesis genesisBlock = (Genesis) BlockGenerator.getNewGenesisBlock(0, preMineMap);

        List<Block> blocks = BlockGenerator.getSimpleBlockChain(genesisBlock, 10);

        TransactionReceipt receipt1 = new TransactionReceipt();
        org.ethereum.core.Transaction rskTx1 = new SimpleRskTransaction(hash1.getBytes());
        receipt1.setTransaction(rskTx1);
        TransactionInfo ti1 = new TransactionInfo(receipt1, blocks.get(1).getHash(), 0);

        List<TransactionInfo> tis = Lists.newArrayList(ti1);
        BlockchainBuilder builder = new BlockchainBuilder();

        Blockchain blockchain = builder.setTesting(true).setRsk(true).setTransactionInfos(tis).setGenesis(genesisBlock).build();
        blocks.get(1).getTransactionsList().add(rskTx1);
        blocks.get(1).flushRLP();
        for (int k = 0; k < blocks.size(); k++)
            blockchain.getBlockStore().saveBlock(blocks.get(k), BigInteger.ONE, true);

        org.ethereum.core.Block rskCurrentBlock = blocks.get(9);
        ReceiptStore rskReceiptStore = blockchain.getReceiptStore();
        org.ethereum.db.BlockStore rskBlockStore = blockchain.getBlockStore();

        Repository repository = blockchain.getRepository();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);

        provider0.getRskTxsWaitingForConfirmations().put(hash1, tx1);
        provider0.getBtcUTXOs().add(new UTXO(PegTestUtils.createHash(), 1, Coin.COIN.add(Coin.valueOf(100)), 0, false, ScriptBuilder.createOutputScript(bridgeConstants.getFederationAddress())));

        provider0.save();

        track.commit();

        track = repository.startTracking();

        BridgeSupport bridgeSupport = new BridgeSupport(track, PrecompiledContracts.BRIDGE_ADDR, rskCurrentBlock, rskReceiptStore, rskBlockStore);

        bridgeSupport.updateCollections();

        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR);

        Assert.assertEquals(0, provider.getRskTxsWaitingForConfirmations().size());
        Assert.assertEquals(1, provider.getRskTxsWaitingForSignatures().size());
        Assert.assertEquals(Denomination.satoshisToWeis(BigInteger.valueOf(21000000-2600)), repository.getBalance(Hex.decode(PrecompiledContracts.BRIDGE_ADDR)));
        Assert.assertEquals(Denomination.satoshisToWeis(BigInteger.valueOf(2600)), repository.getBalance(SystemProperties.CONFIG.getBlockchainConfig().getCommonConstants().getBurnAddress()));
    }

    @Test
    public void callUpdateCollectionsWithTransactionsWaitingForConfirmationWithEnoughConfirmationsAndFunds() throws IOException, BlockStoreException {
        BtcTransaction tx1 = createTransaction();
        BtcTransaction tx2 = createTransaction();
        BtcTransaction tx3 = createTransaction();
        Sha3Hash hash1 = PegTestUtils.createHash3();
        Sha3Hash hash2 = PegTestUtils.createHash3();
        Sha3Hash hash3 = PegTestUtils.createHash3();

        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        BridgeStorageProvider provider0 = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);

        provider0.getRskTxsWaitingForConfirmations().put(hash1, tx1);
        provider0.getRskTxsWaitingForConfirmations().put(hash2, tx2);
        provider0.getRskTxsWaitingForConfirmations().put(hash3, tx3);

        provider0.save();

        track.commit();

        track = repository.startTracking();
        BridgeConstants bridgeConstants = SystemProperties.CONFIG.getBlockchainConfig().getCommonConstants().getBridgeConstants();
        Context context = new Context(bridgeConstants.getBtcParams());
        BridgeStorageProvider provider = new SimpleBridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR, new SimpleWallet(context));

        List<Block> blocks = BlockGenerator.getSimpleBlockChain(BlockGenerator.getGenesisBlock(), 10);
        TransactionReceipt receipt = new TransactionReceipt();
        org.ethereum.core.Transaction tx = new SimpleRskTransaction(hash1.getBytes());
        receipt.setTransaction(tx);
        TransactionInfo ti = new TransactionInfo(receipt, blocks.get(1).getHash(), 0);
        List<TransactionInfo> tis = new ArrayList<>();
        tis.add(ti);
        blocks.get(1).getTransactionsList().add(tx);
        blocks.get(1).flushRLP();

        BlockchainBuilder builder = new BlockchainBuilder();
        Blockchain blockchain = builder.setTesting(true).setRsk(true).setTransactionInfos(tis).build();

        for (int k = 0; k < blocks.size(); k++)
            blockchain.getBlockStore().saveBlock(blocks.get(k), BigInteger.ONE, true);

        org.ethereum.core.Block rskCurrentBlock = blocks.get(9);
        ReceiptStore rskReceiptStore = blockchain.getReceiptStore();
        org.ethereum.db.BlockStore rskBlockStore = blockchain.getBlockStore();

        BridgeSupport bridgeSupport = new BridgeSupport(track, PrecompiledContracts.BRIDGE_ADDR, provider, rskCurrentBlock, rskReceiptStore, rskBlockStore);

        bridgeSupport.updateCollections();
        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider2 = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR);

        Assert.assertFalse(provider2.getRskTxsWaitingForConfirmations().isEmpty());
        Assert.assertEquals(2, provider2.getRskTxsWaitingForConfirmations().size());
        Assert.assertFalse(provider2.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertEquals(1, provider2.getRskTxsWaitingForSignatures().size());
    }

    @Test
    public void sendOrphanBlockHeader() throws IOException, BlockStoreException {
        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        BridgeConstants bridgeConstants = SystemProperties.CONFIG.getBlockchainConfig().getCommonConstants().getBridgeConstants();
        Context btcContext = new Context(bridgeConstants.getBtcParams());
        BtcBlockStore btcBlockStore = new RepositoryBlockStore(track, contractAddress);
        BtcBlockChain btcBlockChain = new BtcBlockChain(btcContext, btcBlockStore);

        BridgeStorageProvider provider = new BridgeStorageProvider(track, contractAddress);

        BridgeSupport bridgeSupport = new BridgeSupport(track, contractAddress, provider, btcBlockStore, btcBlockChain);

        co.rsk.bitcoinj.core.BtcBlock block = new co.rsk.bitcoinj.core.BtcBlock(btcParams, 1, PegTestUtils.createHash(), PegTestUtils.createHash(), 1, 1, 1, new ArrayList<BtcTransaction>());
        co.rsk.bitcoinj.core.BtcBlock[] headers = new co.rsk.bitcoinj.core.BtcBlock[1];
        headers[0] = block;

        bridgeSupport.receiveHeaders(headers);
        bridgeSupport.save();

        track.commit();

        Assert.assertNull(btcBlockStore.get(block.getHash()));
    }

    @Test
    public void addBlockHeaderToBlockchain() throws IOException, BlockStoreException {
        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        BridgeConstants bridgeConstants = SystemProperties.CONFIG.getBlockchainConfig().getCommonConstants().getBridgeConstants();
        Context btcContext = new Context(bridgeConstants.getBtcParams());
        BtcBlockStore btcBlockStore = new RepositoryBlockStore(track, contractAddress);
        BtcBlockChain btcBlockChain = new SimpleBlockChain(btcContext, btcBlockStore);

        BridgeStorageProvider provider = new BridgeStorageProvider(track, contractAddress);

        BridgeSupport bridgeSupport = new BridgeSupport(track, contractAddress, provider, btcBlockStore, btcBlockChain);

        co.rsk.bitcoinj.core.BtcBlock block = new co.rsk.bitcoinj.core.BtcBlock(btcParams, 1, PegTestUtils.createHash(), PegTestUtils.createHash(), 1, 1, 1, new ArrayList<BtcTransaction>());
        co.rsk.bitcoinj.core.BtcBlock[] headers = new co.rsk.bitcoinj.core.BtcBlock[1];
        headers[0] = block;

        bridgeSupport.receiveHeaders(headers);
        bridgeSupport.save();

        track.commit();

        Assert.assertNotNull(btcBlockStore.get(block.getHash()));
    }

    @Test
    public void addSignatureToMissingTransaction() throws Exception {
        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        BridgeSupport bridgeSupport = new BridgeSupport(track, PrecompiledContracts.BRIDGE_ADDR, (Block) null, null, null);

        bridgeSupport.addSignature(1, bridgeConstants.getFederatorPublicKeys().get(0), null, PegTestUtils.createHash().getBytes());
        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR);

        Assert.assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertTrue(provider.getRskTxsWaitingForBroadcasting().isEmpty());
    }

    @Test
    public void addSignatureFromInvalidFederator() throws Exception {
        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        BridgeSupport bridgeSupport = new BridgeSupport(track, PrecompiledContracts.BRIDGE_ADDR, (Block) null, null, null);

        bridgeSupport.addSignature(1, new BtcECKey(), null, PegTestUtils.createHash().getBytes());
        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR);

        Assert.assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertTrue(provider.getRskTxsWaitingForBroadcasting().isEmpty());
    }

    @Test
    public void addSignatureWithInvalidSignature() throws Exception {
        addSignatureFromValidFederator(Lists.newArrayList(new BtcECKey()), 1, true, false, "InvalidParameters");
    }

    @Test
    public void addSignatureWithLessSignaturesThanExpected() throws Exception {
        List<BtcECKey> keys = Lists.newArrayList(((BridgeRegTestConstants)bridgeConstants).getFederatorPrivateKeys().get(0));
        addSignatureFromValidFederator(keys, 0, true, false, "InvalidParameters");
    }

    @Test
    public void addSignatureWithMoreSignaturesThanExpected() throws Exception {
        List<BtcECKey> keys = Lists.newArrayList(((BridgeRegTestConstants)bridgeConstants).getFederatorPrivateKeys().get(0));
        addSignatureFromValidFederator(keys, 2, true, false, "InvalidParameters");
    }

    @Test
    public void addSignatureNonCanonicalSignature() throws Exception {
        List<BtcECKey> keys = Lists.newArrayList(((BridgeRegTestConstants)bridgeConstants).getFederatorPrivateKeys().get(0));
        addSignatureFromValidFederator(keys, 1, false, false, "InvalidParameters");
    }

    @Test
    public void addSignatureTwice() throws Exception {
        List<BtcECKey> keys = Lists.newArrayList(((BridgeRegTestConstants)bridgeConstants).getFederatorPrivateKeys().get(0));
        addSignatureFromValidFederator(keys, 1, true, true, "PartiallySigned");
    }

    @Test
    public void addSignatureOneSignature() throws Exception {
        List<BtcECKey> keys = Lists.newArrayList(((BridgeRegTestConstants)bridgeConstants).getFederatorPrivateKeys().get(0));
        addSignatureFromValidFederator(keys, 1, true, false, "PartiallySigned");
    }

    @Test
    public void addSignatureTwoSignatures() throws Exception {
        List<BtcECKey> federatorPrivateKeys = ((BridgeRegTestConstants)bridgeConstants).getFederatorPrivateKeys();
        List<BtcECKey> keys = Lists.newArrayList(federatorPrivateKeys.get(0), federatorPrivateKeys.get(1));
        addSignatureFromValidFederator(keys, 1, true, false, "FullySigned");
    }

    /**
     * Helper method to test addSignature() with a valid federatorPublicKey parameter and both valid/invalid signatures
     * @param privateKeysToSignWith keys used to sign the tx. Federator key when we want to produce a valid signature, a random key when we want to produce an invalid signature
     * @param numberOfInputsToSign There is just 1 input. 1 when testing the happy case, other values to test attacks/bugs.
     * @param signatureCanonical Signature should be canonical. true when testing the happy case, false to test attacks/bugs.
     * @param signTwice Sign again with the same key
     * @param expectedResult "InvalidParameters", "PartiallySigned" or "FullySigned"
     */
    private void addSignatureFromValidFederator(List<BtcECKey> privateKeysToSignWith, int numberOfInputsToSign, boolean signatureCanonical, boolean signTwice, String expectedResult) throws Exception {
        Repository repository = new RepositoryImpl();

        final Sha3Hash sha3Hash = PegTestUtils.createHash3();

        Repository track = repository.startTracking();
        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);

        BtcTransaction prevTx = new BtcTransaction(btcParams);
        TransactionOutput prevOut = new TransactionOutput(btcParams, prevTx, Coin.FIFTY_COINS, bridgeConstants.getFederationAddress());
        prevTx.addOutput(prevOut);
        UTXO utxo = new UTXO(prevTx.getHash(), 0, prevOut.getValue(), 0, false, prevOut.getScriptPubKey());
        provider.getBtcUTXOs().add(utxo);

        BtcTransaction t = new BtcTransaction(btcParams);
        TransactionOutput output = new TransactionOutput(btcParams, t, Coin.COIN, new BtcECKey().toAddress(btcParams));
        t.addOutput(output);
        t.addInput(prevOut).setScriptSig(PegTestUtils.createBaseInputScriptThatSpendsFromTheFederation(bridgeConstants));
        provider.getRskTxsWaitingForSignatures().put(sha3Hash, t);
        provider.save();
        track.commit();

        track = repository.startTracking();
        BridgeSupport bridgeSupport = new BridgeSupport(track, contractAddress, (Block) null, null, null);

        Script inputScript = t.getInputs().get(0).getScriptSig();
        List<ScriptChunk> chunks = inputScript.getChunks();
        byte[] program = chunks.get(chunks.size() - 1).data;
        Script redeemScript = new Script(program);
        Sha256Hash sighash = t.hashForSignature(0, redeemScript, BtcTransaction.SigHash.ALL, false);

        BtcECKey.ECDSASignature sig = privateKeysToSignWith.get(0).sign(sighash);
        if (!signatureCanonical) {
            sig = new BtcECKey.ECDSASignature(sig.r, BtcECKey.CURVE.getN().subtract(sig.s));
        }
        byte[] derEncodedSig = sig.encodeToDER();

        List derEncodedSigs = new ArrayList();
        for (int i = 0; i < numberOfInputsToSign; i++) {
            derEncodedSigs.add(derEncodedSig);
        }
        bridgeSupport.addSignature(1, bridgeConstants.getFederatorPublicKeys().get(0), derEncodedSigs, sha3Hash.getBytes());
        if (signTwice) {
            // Create another valid signature with the same private key
            ECDSASigner signer = new ECDSASigner();
            ECPrivateKeyParameters privKey = new ECPrivateKeyParameters(privateKeysToSignWith.get(0).getPrivKey(), BtcECKey.CURVE);
            signer.init(true, privKey);
            BigInteger[] components = signer.generateSignature(sighash.getBytes());
            BtcECKey.ECDSASignature sig2 = new BtcECKey.ECDSASignature(components[0], components[1]).toCanonicalised();
            bridgeSupport.addSignature(1, bridgeConstants.getFederatorPublicKeys().get(0), Lists.newArrayList(sig2.encodeToDER()), sha3Hash.getBytes());
        }
        if (privateKeysToSignWith.size()>1) {
            BtcECKey.ECDSASignature sig2 = privateKeysToSignWith.get(1).sign(sighash);
            byte[] derEncodedSig2 = sig2.encodeToDER();
            List derEncodedSigs2 = new ArrayList();
            for (int i = 0; i < numberOfInputsToSign; i++) {
                derEncodedSigs2.add(derEncodedSig2);
            }
            bridgeSupport.addSignature(1, bridgeConstants.getFederatorPublicKeys().get(1), derEncodedSigs2, sha3Hash.getBytes());
        }
        bridgeSupport.save();
        track.commit();

        provider = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR);

        if ("FullySigned".equals(expectedResult)) {
            Assert.assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
            Script retrievedScriptSig = provider.getRskTxsWaitingForBroadcasting().get(sha3Hash).getLeft().getInput(0).getScriptSig();
            Assert.assertEquals(4, retrievedScriptSig.getChunks().size());
            Assert.assertEquals(true, retrievedScriptSig.getChunks().get(1).data.length > 0);
            Assert.assertEquals(true, retrievedScriptSig.getChunks().get(2).data.length > 0);
            Assert.assertTrue(provider.getBtcUTXOs().isEmpty());
        } else {
            Script retrievedScriptSig = provider.getRskTxsWaitingForSignatures().get(sha3Hash).getInput(0).getScriptSig();
            Assert.assertEquals(4, retrievedScriptSig.getChunks().size());
            boolean expectSignatureToBePersisted = false; // for "InvalidParameters"
            if ("PartiallySigned".equals(expectedResult)) {
                expectSignatureToBePersisted = true;
            }
            Assert.assertEquals(expectSignatureToBePersisted, retrievedScriptSig.getChunks().get(1).data.length > 0);
            Assert.assertEquals(false, retrievedScriptSig.getChunks().get(2).data.length > 0);
            Assert.assertTrue(provider.getRskTxsWaitingForBroadcasting().isEmpty());
            Assert.assertFalse(provider.getBtcUTXOs().isEmpty());
        }
    }

    @Test
    public void releaseBtcWithDustOutput() throws BlockStoreException, AddressFormatException, IOException {
        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        Sha3Hash hash = PegTestUtils.createHash3();
        org.ethereum.core.Transaction tx = org.ethereum.core.Transaction.create(TO_ADDRESS, DUST_AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);;

        tx.sign(new org.ethereum.crypto.ECKey().getPrivKeyBytes());

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);
        BridgeSupport bridgeSupport = new BridgeSupport(track, PrecompiledContracts.BRIDGE_ADDR, provider, null, null, null);

        bridgeSupport.releaseBtc(tx);
        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider2 = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR);

        Assert.assertTrue(provider.getRskTxsWaitingForConfirmations().isEmpty());
        Assert.assertTrue(provider2.getRskTxsWaitingForConfirmations().isEmpty());
        Assert.assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertTrue(provider.getRskTxsWaitingForBroadcasting().isEmpty());
    }

    @Test
    public void releaseBtc() throws BlockStoreException, AddressFormatException, IOException {
        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        Sha3Hash hash = PegTestUtils.createHash3();
        org.ethereum.core.Transaction tx = org.ethereum.core.Transaction.create(TO_ADDRESS, AMOUNT, NONCE, GAS_PRICE, GAS_LIMIT, DATA);;

        tx.sign(new org.ethereum.crypto.ECKey().getPrivKeyBytes());

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);
        BridgeSupport bridgeSupport = new BridgeSupport(track, PrecompiledContracts.BRIDGE_ADDR, provider, null, null, null);

        bridgeSupport.releaseBtc(tx);
        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider2 = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR);

        Assert.assertFalse(provider.getRskTxsWaitingForConfirmations().isEmpty());
        Assert.assertFalse(provider2.getRskTxsWaitingForConfirmations().isEmpty());
        Assert.assertTrue(provider.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertTrue(provider.getRskTxsWaitingForBroadcasting().isEmpty());
    }



    @Test
    public void registerBtcTransactionOfAlreadyProcessedTransaction() throws BlockStoreException, AddressFormatException, IOException {
        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        BtcTransaction tx = createTransaction();
        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);

        provider.getBtcTxHashesAlreadyProcessed().add(tx.getHash());

        BridgeSupport bridgeSupport = new BridgeSupport(track, PrecompiledContracts.BRIDGE_ADDR, provider, null, null, null);

        bridgeSupport.registerBtcTransaction(tx, 0, null);
        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider2 = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR);

        Assert.assertTrue(provider2.getBtcUTXOs().isEmpty());
        Assert.assertTrue(provider2.getRskTxsWaitingForConfirmations().isEmpty());
        Assert.assertTrue(provider2.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertTrue(provider2.getRskTxsWaitingForBroadcasting().isEmpty());
        Assert.assertFalse(provider2.getBtcTxHashesAlreadyProcessed().isEmpty());
    }

    @Test
    public void registerBtcTransactionOfTransactionNotInMerkleTree() throws BlockStoreException, AddressFormatException, IOException {
        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        BtcTransaction tx = createTransaction();
        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);

        BridgeSupport bridgeSupport = new BridgeSupport(track, PrecompiledContracts.BRIDGE_ADDR, provider, null, null, null);

        byte[] bits = new byte[1];
        bits[0] = 0x01;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(PegTestUtils.createHash());

        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 1);

        bridgeSupport.registerBtcTransaction(tx, 0, pmt);
        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider2 = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR);

        Assert.assertTrue(provider2.getBtcUTXOs().isEmpty());
        Assert.assertTrue(provider2.getRskTxsWaitingForConfirmations().isEmpty());
        Assert.assertTrue(provider2.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertTrue(provider2.getRskTxsWaitingForBroadcasting().isEmpty());
        Assert.assertTrue(provider2.getBtcTxHashesAlreadyProcessed().isEmpty());
    }

    @Test
    public void registerBtcTransactionOfTransactionInMerkleTreeWithNegativeHeight() throws BlockStoreException, AddressFormatException, IOException {
        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        BtcTransaction tx = createTransaction();
        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);

        BridgeSupport bridgeSupport = new BridgeSupport(track, PrecompiledContracts.BRIDGE_ADDR, provider, null, null, null);

        byte[] bits = new byte[1];
        bits[0] = 0x01;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx.getHash());

        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 1);

        bridgeSupport.registerBtcTransaction(tx, -1, pmt);
        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider2 = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR);

        Assert.assertTrue(provider2.getBtcUTXOs().isEmpty());
        Assert.assertTrue(provider2.getRskTxsWaitingForConfirmations().isEmpty());
        Assert.assertTrue(provider2.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertTrue(provider2.getRskTxsWaitingForBroadcasting().isEmpty());
        Assert.assertTrue(provider2.getBtcTxHashesAlreadyProcessed().isEmpty());
    }

    @Test
    public void registerBtcTransactionOfTransactionInMerkleTreeWithNotEnoughtHeight() throws BlockStoreException, AddressFormatException, IOException {
        BlockchainNetConfig blockchainNetConfigOriginal = SystemProperties.CONFIG.getBlockchainConfig();
        SystemProperties.CONFIG.setBlockchainConfig(new RegTestConfig());
        NetworkParameters _networkParameters = SystemProperties.CONFIG.getBlockchainConfig().getCommonConstants().getBridgeConstants().getBtcParams();

        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        BtcTransaction tx = createTransaction();
        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);

        BridgeSupport bridgeSupport = new BridgeSupport(track, PrecompiledContracts.BRIDGE_ADDR, provider, null, null, null);

        byte[] bits = new byte[1];
        bits[0] = 0x01;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx.getHash());

        PartialMerkleTree pmt = new PartialMerkleTree(_networkParameters, bits, hashes, 1);

        bridgeSupport.registerBtcTransaction(tx, 1, pmt);
        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider2 = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR);

        Assert.assertTrue(provider2.getBtcUTXOs().isEmpty());
        Assert.assertTrue(provider2.getRskTxsWaitingForConfirmations().isEmpty());
        Assert.assertTrue(provider2.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertTrue(provider2.getRskTxsWaitingForBroadcasting().isEmpty());
        Assert.assertTrue(provider2.getBtcTxHashesAlreadyProcessed().isEmpty());

        SystemProperties.CONFIG.setBlockchainConfig(blockchainNetConfigOriginal);
    }

    @Test
    public void registerBtcTransactionTxNotLockNorReleaseTx() throws BlockStoreException, AddressFormatException, IOException {
        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        BridgeConstants bridgeConstants = SystemProperties.CONFIG.getBlockchainConfig().getCommonConstants().getBridgeConstants();

        BtcTransaction tx = new BtcTransaction(this.btcParams);
        Address address = ScriptBuilder.createP2SHOutputScript(2, Lists.newArrayList(new BtcECKey(), new BtcECKey(), new BtcECKey())).getToAddress(bridgeConstants.getBtcParams());
        tx.addOutput(Coin.COIN, address);
        tx.addInput(PegTestUtils.createHash(), 0, ScriptBuilder.createInputScript(null, new BtcECKey()));


        Context btcContext = new Context(bridgeConstants.getBtcParams());
        BtcBlockStore btcBlockStore = new RepositoryBlockStore(track, contractAddress);
        BtcBlockChain btcBlockChain = new SimpleBlockChain(btcContext, btcBlockStore);

        BridgeStorageProvider provider = new BridgeStorageProvider(track, contractAddress);

        BridgeSupport bridgeSupport = new BridgeSupport(track, contractAddress, provider, btcBlockStore, btcBlockChain);

        byte[] bits = new byte[1];
        bits[0] = 0x01;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx.getHash());

        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock block = new co.rsk.bitcoinj.core.BtcBlock(btcParams, 1, PegTestUtils.createHash(), merkleRoot, 1, 1, 1, new ArrayList<BtcTransaction>());

        btcBlockChain.add(block);

        bridgeSupport.registerBtcTransaction(tx, 1, pmt);
        bridgeSupport.save();

        track.commit();

        BridgeStorageProvider provider2 = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR);

        Assert.assertEquals(0, provider2.getBtcUTXOs().size());

        Assert.assertTrue(provider2.getRskTxsWaitingForConfirmations().isEmpty());
        Assert.assertTrue(provider2.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertTrue(provider2.getRskTxsWaitingForBroadcasting().isEmpty());
        Assert.assertTrue(provider2.getBtcTxHashesAlreadyProcessed().isEmpty());
    }

    @Test
    public void registerBtcTransactionReleaseTx() throws BlockStoreException, AddressFormatException, IOException {
        Repository repository = new RepositoryImpl();
        repository.addBalance(Hex.decode(PrecompiledContracts.BRIDGE_ADDR), BigInteger.valueOf(21000000).multiply(Denomination.SBTC.value()));
        Repository track = repository.startTracking();

        BridgeRegTestConstants bridgeConstants = (BridgeRegTestConstants) SystemProperties.CONFIG.getBlockchainConfig().getCommonConstants().getBridgeConstants();

        BtcTransaction tx = new BtcTransaction(this.btcParams);
        Address address = ScriptBuilder.createP2SHOutputScript(2, Lists.newArrayList(new BtcECKey(), new BtcECKey(), new BtcECKey())).getToAddress(bridgeConstants.getBtcParams());
        tx.addOutput(Coin.COIN, address);
        Address address2 = bridgeConstants.getFederationAddress();
        tx.addOutput(Coin.COIN, address2);

        // Create previous tx
        BtcTransaction prevTx = new BtcTransaction(btcParams);
        TransactionOutput prevOut = new TransactionOutput(btcParams, prevTx, Coin.FIFTY_COINS, bridgeConstants.getFederationAddress());
        prevTx.addOutput(prevOut);
        // Create tx input
        tx.addInput(prevOut);
        // Create tx input base script sig
        Script scriptSig = PegTestUtils.createBaseInputScriptThatSpendsFromTheFederation(bridgeConstants);
        // Create sighash
        Script redeemScript = ScriptBuilder.createRedeemScript(bridgeConstants.getFederatorsRequiredToSign(), bridgeConstants.getFederatorPublicKeys());
        Sha256Hash sighash = tx.hashForSignature(0, redeemScript, BtcTransaction.SigHash.ALL, false);
        // Sign by federator 0
        BtcECKey.ECDSASignature sig0 = bridgeConstants.getFederatorPrivateKeys().get(0).sign(sighash);
        TransactionSignature txSig0 = new TransactionSignature(sig0, BtcTransaction.SigHash.ALL, false);
        int sigIndex0 = scriptSig.getSigInsertionIndex(sighash, bridgeConstants.getFederatorPublicKeys().get(0));
        scriptSig = ScriptBuilder.updateScriptWithSignature(scriptSig, txSig0.encodeToBitcoin(), sigIndex0, 1, 1);
        // Sign by federator 1
        BtcECKey.ECDSASignature sig1 = bridgeConstants.getFederatorPrivateKeys().get(1).sign(sighash);
        TransactionSignature txSig1 = new TransactionSignature(sig1, BtcTransaction.SigHash.ALL, false);
        int sigIndex1 = scriptSig.getSigInsertionIndex(sighash, bridgeConstants.getFederatorPublicKeys().get(1));
        scriptSig = ScriptBuilder.updateScriptWithSignature(scriptSig, txSig1.encodeToBitcoin(), sigIndex1, 1, 1);
        // Set scipt sign to tx input
        tx.getInput(0).setScriptSig(scriptSig);

        Context btcContext = new Context(bridgeConstants.getBtcParams());
        BtcBlockStore btcBlockStore = new RepositoryBlockStore(track, contractAddress);
        BtcBlockChain btcBlockChain = new SimpleBlockChain(btcContext, btcBlockStore);

        BridgeStorageProvider provider = new BridgeStorageProvider(track, contractAddress);

        BridgeSupport bridgeSupport = new BridgeSupport(track, contractAddress, provider, btcBlockStore, btcBlockChain);

        byte[] bits = new byte[1];
        bits[0] = 0x01;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx.getHash());

        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock block = new co.rsk.bitcoinj.core.BtcBlock(btcParams, 1, PegTestUtils.createHash(), merkleRoot, 1, 1, 1, new ArrayList<BtcTransaction>());

        btcBlockChain.add(block);
        ((SimpleBlockChain)btcBlockChain).useHighBlock();
        bridgeSupport.registerBtcTransaction(tx, 1, pmt);
        bridgeSupport.save();
        ((SimpleBlockChain)btcBlockChain).useBlock();

        track.commit();

        Assert.assertEquals(BigInteger.valueOf(21000000).multiply(Denomination.SBTC.value()), repository.getBalance(Hex.decode(PrecompiledContracts.BRIDGE_ADDR)));

        BridgeStorageProvider provider2 = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR);

        Assert.assertEquals(1, provider2.getBtcUTXOs().size());
        Assert.assertEquals(Coin.COIN, provider2.getBtcUTXOs().get(0).getValue());

        Assert.assertTrue(provider2.getRskTxsWaitingForConfirmations().isEmpty());
        Assert.assertTrue(provider2.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertTrue(provider2.getRskTxsWaitingForBroadcasting().isEmpty());
        Assert.assertEquals(1, provider2.getBtcTxHashesAlreadyProcessed().size());
    }

    @Test
    public void registerBtcTransactionLockTx() throws BlockStoreException, AddressFormatException, IOException {
        Repository repository = new RepositoryImpl();
        repository.addBalance(Hex.decode(PrecompiledContracts.BRIDGE_ADDR), BigInteger.valueOf(21000000).multiply(Denomination.SBTC.value()));

        Repository track = repository.startTracking();

        BridgeConstants bridgeConstants = SystemProperties.CONFIG.getBlockchainConfig().getCommonConstants().getBridgeConstants();

        BtcTransaction tx = new BtcTransaction(this.btcParams);
        Address address = bridgeConstants.getFederationAddress();
        tx.addOutput(Coin.COIN, address);
        BtcECKey srcKey = new BtcECKey();
        tx.addInput(PegTestUtils.createHash(), 0, ScriptBuilder.createInputScript(null, srcKey));

        Context btcContext = new Context(bridgeConstants.getBtcParams());
        BtcBlockStore btcBlockStore = new RepositoryBlockStore(track, contractAddress);
        BtcBlockChain btcBlockChain = new SimpleBlockChain(btcContext, btcBlockStore);

        BridgeStorageProvider provider = new BridgeStorageProvider(track, contractAddress);

        BridgeSupport bridgeSupport = new BridgeSupport(track, contractAddress, provider, btcBlockStore, btcBlockChain);

        byte[] bits = new byte[1];
        bits[0] = 0x01;
        List<Sha256Hash> hashes = new ArrayList<>();
        hashes.add(tx.getHash());

        PartialMerkleTree pmt = new PartialMerkleTree(btcParams, bits, hashes, 1);
        List<Sha256Hash> hashlist = new ArrayList<>();
        Sha256Hash merkleRoot = pmt.getTxnHashAndMerkleRoot(hashlist);

        co.rsk.bitcoinj.core.BtcBlock block = new co.rsk.bitcoinj.core.BtcBlock(btcParams, 1, PegTestUtils.createHash(), merkleRoot, 1, 1, 1, new ArrayList<BtcTransaction>());

        btcBlockChain.add(block);

        ((SimpleBlockChain)btcBlockChain).useHighBlock();
        bridgeSupport.registerBtcTransaction(tx, 1, pmt);
        bridgeSupport.save();
        ((SimpleBlockChain)btcBlockChain).useBlock();

        track.commit();

        byte[] srcKeyRskAddress = org.ethereum.crypto.ECKey.fromPrivate(srcKey.getPrivKey()).getAddress();
        Assert.assertEquals(Denomination.SBTC.value(), repository.getBalance(srcKeyRskAddress));
        Assert.assertEquals(BigInteger.valueOf(21000000).multiply(Denomination.SBTC.value()).subtract(Denomination.SBTC.value()), repository.getBalance(Hex.decode(PrecompiledContracts.BRIDGE_ADDR)));

        BridgeStorageProvider provider2 = new BridgeStorageProvider(repository, PrecompiledContracts.BRIDGE_ADDR);

        Assert.assertEquals(1, provider2.getBtcUTXOs().size());
        Assert.assertEquals(Coin.COIN, provider2.getBtcUTXOs().get(0).getValue());

        Assert.assertTrue(provider2.getRskTxsWaitingForConfirmations().isEmpty());
        Assert.assertTrue(provider2.getRskTxsWaitingForSignatures().isEmpty());
        Assert.assertTrue(provider2.getRskTxsWaitingForBroadcasting().isEmpty());
        Assert.assertEquals(1, provider2.getBtcTxHashesAlreadyProcessed().size());
    }

    @Test
    public void testHasEnoughConfirmations() throws Exception {
        Assert.assertFalse(hasEnoughConfirmations(10));
        Assert.assertTrue(hasEnoughConfirmations(20));
    }

    public boolean hasEnoughConfirmations(long currentBlockNumber) throws Exception{
        Repository repository = new RepositoryImpl();
        Repository track = repository.startTracking();

        byte[] blockHash = new byte[32];
        new SecureRandom().nextBytes(blockHash);
        TransactionInfo transactionInfo = Mockito.mock(TransactionInfo.class);
        Mockito.when(transactionInfo.getBlockHash()).thenReturn(blockHash);

        ReceiptStore receiptStore = Mockito.mock(ReceiptStore.class);
        Mockito.when(receiptStore.get(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(transactionInfo);

        org.ethereum.core.Block includedBlock = Mockito.mock(org.ethereum.core.Block.class);
        Mockito.when(includedBlock.getNumber()).thenReturn(Long.valueOf(10));

        org.ethereum.db.BlockStore blockStore = Mockito.mock(org.ethereum.db.BlockStore.class);
        Mockito.when(blockStore.getBlockByHash(Mockito.any())).thenReturn(includedBlock);

        org.ethereum.core.Block currentBlock = Mockito.mock(org.ethereum.core.Block.class);
        Mockito.when(currentBlock.getNumber()).thenReturn(Long.valueOf(currentBlockNumber));

        BridgeStorageProvider provider = new BridgeStorageProvider(track, PrecompiledContracts.BRIDGE_ADDR);
        BridgeSupport bridgeSupport = new BridgeSupport(track, PrecompiledContracts.BRIDGE_ADDR, provider, currentBlock, receiptStore, blockStore);

        Sha3Hash txHash = Sha3Hash.ZERO_HASH;

        return bridgeSupport.hasEnoughConfirmations(txHash);
    }

    private BtcTransaction createTransaction() {
        BtcTransaction btcTx = new BtcTransaction(btcParams);
        btcTx.addInput(new TransactionInput(btcParams, btcTx, new byte[0]));
        btcTx.addOutput(new TransactionOutput(btcParams, btcTx, Coin.COIN, new BtcECKey().toAddress(btcParams)));
        return btcTx;
        //new SimpleBtcTransaction(btcParams, PegTestUtils.createHash());
    }
}
