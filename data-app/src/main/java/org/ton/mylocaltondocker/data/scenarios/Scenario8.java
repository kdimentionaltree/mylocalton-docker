package org.ton.mylocaltondocker.data.scenarios;

import static org.ton.mylocaltondocker.data.controller.StartUpTask.dataHighloadFaucetAddress;

import com.iwebpp.crypto.TweetNaclFast;
import lombok.extern.slf4j.Slf4j;
import org.ton.java.address.Address;
import org.ton.java.smartcontract.types.WalletV4R2Config;
import org.ton.java.smartcontract.wallet.v4.WalletV4R2;
import org.ton.java.tonlib.Tonlib;
import org.ton.java.tonlib.types.ExtMessageInfo;
import org.ton.java.utils.Utils;
import org.ton.mylocaltondocker.data.db.DB;

/** to up V4R2 wallet, upload state-init with random wallet-id, send back to faucet 0.06 */
@Slf4j
public class Scenario8 implements Scenario {

  Tonlib tonlib;

  public Scenario8(Tonlib tonlib) {
    this.tonlib = tonlib;
  }

  public void run() {
    log.info("STARTED SCENARIO 8");

    long walletId = Math.abs(Utils.getRandomInt());
    TweetNaclFast.Signature.KeyPair keyPair = Utils.generateSignatureKeyPair();

    WalletV4R2 contract =
        WalletV4R2.builder().tonlib(tonlib).keyPair(keyPair).walletId(walletId).build();

    Address walletAddress = contract.getAddress();

    String nonBounceableAddress = walletAddress.toNonBounceable();
    DB.addRequest(nonBounceableAddress, Utils.toNano(0.1));
    tonlib.waitForBalanceChange(contract.getAddress(), 60);
    ExtMessageInfo extMessageInfo = contract.deploy();
    log.info("deploy8 {}", extMessageInfo);
    contract.waitForDeployment();

    long walletCurrentSeqno = contract.getSeqno();
    log.info("walletV4 balance: {}", Utils.formatNanoValue(contract.getBalance()));
    log.info("seqno: {}", walletCurrentSeqno);
    log.info("walletId: {}", contract.getWalletId());
    log.info("pubKey: {}", Utils.bytesToHex(contract.getPublicKey()));
    log.info("pluginsList: {}", contract.getPluginsList());

    WalletV4R2Config config =
        WalletV4R2Config.builder()
            .operation(0)
            .walletId(contract.getWalletId())
            .seqno(contract.getSeqno())
            .destination(dataHighloadFaucetAddress)
            .mode(3)
            .amount(Utils.toNano(0.06))
            .comment("mlt-scenario8")
            .build();

    contract.send(config);
    contract.waitForBalanceChangeWithTolerance(60, Utils.toNano(0.05));

    log.info("FINISHED SCENARIO 8");
  }
}
