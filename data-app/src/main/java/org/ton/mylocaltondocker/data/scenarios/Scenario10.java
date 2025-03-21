package org.ton.mylocaltondocker.data.scenarios;

import static org.ton.mylocaltondocker.data.controller.StartUpTask.dataHighloadFaucetAddress;

import com.iwebpp.crypto.TweetNaclFast;
import java.math.BigInteger;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;
import org.ton.java.smartcontract.types.Destination;
import org.ton.java.smartcontract.types.WalletV5Config;
import org.ton.java.smartcontract.wallet.v5.WalletV5;
import org.ton.java.tonlib.Tonlib;
import org.ton.java.utils.Utils;
import org.ton.mylocaltondocker.data.db.DB;

/** to up V5R1 wallet, upload state-init, send back to faucet */
@Slf4j
public class Scenario10 implements Scenario {
  Tonlib tonlib;

  public Scenario10(Tonlib tonlib) {
    this.tonlib = tonlib;
  }

  public void run() {
    log.info("STARTED SCENARIO 10");
    TweetNaclFast.Signature.KeyPair keyPair = Utils.generateSignatureKeyPair();
    long walletId = Math.abs(Utils.getRandomInt());

    WalletV5 contract =
        WalletV5.builder()
            .tonlib(tonlib)
            .walletId(walletId)
            .keyPair(keyPair)
            .isSigAuthAllowed(true)
            .build();
    String nonBounceableAddress = contract.getAddress().toNonBounceable();
    log.info("v5 address {}", nonBounceableAddress);
    DB.addRequest(nonBounceableAddress, Utils.toNano(0.1));
    tonlib.waitForBalanceChange(contract.getAddress(), 60);

    contract.deploy();

    contract.waitForDeployment();

    WalletV5Config config =
        WalletV5Config.builder()
            .seqno(1)
            .walletId(walletId)
            .body(
                contract
                    .createBulkTransfer(
                        Collections.singletonList(
                            Destination.builder()
                                .bounce(false)
                                .address(dataHighloadFaucetAddress.toRaw())
                                .mode(3)
                                .amount(Utils.toNano(0.03))
                                .comment("mlt-scenario-10")
                                .build()))
                    .toCell())
            .build();

    contract.send(config);
    contract.waitForBalanceChangeWithTolerance(45, Utils.toNano(0.01));

    BigInteger balance = contract.getBalance();
    if (balance.longValue() > Utils.toNano(0.07).longValue()) {
      log.error("scenario10 failed");
    }
    log.info("FINISHED SCENARIO 10");
  }
}
