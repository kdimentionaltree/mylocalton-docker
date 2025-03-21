package org.ton.mylocaltondocker.data.scenarios;

import com.iwebpp.crypto.TweetNaclFast;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.ton.java.smartcontract.types.Destination;
import org.ton.java.smartcontract.types.WalletV5Config;
import org.ton.java.smartcontract.wallet.v5.WalletV5;
import org.ton.java.tonlib.Tonlib;
import org.ton.java.utils.Utils;
import org.ton.mylocaltondocker.data.db.DB;

/** to up V5R1 wallet, upload state-init, transfer to 255 random recipients */
@Slf4j
public class Scenario11 implements Scenario {
  Tonlib tonlib;

  public Scenario11(Tonlib tonlib) {
    this.tonlib = tonlib;
  }

  public void run() throws NoSuchAlgorithmException {
    log.info("STARTED SCENARIO 11");
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
    DB.addRequest(nonBounceableAddress, Utils.toNano(1.5));
    tonlib.waitForBalanceChange(contract.getAddress(), 60);

    contract.deploy();

    contract.waitForDeployment();

    WalletV5Config config =
        WalletV5Config.builder()
            .seqno(1)
            .walletId(walletId)
            .body(contract.createBulkTransfer(createDummyDestinations(255)).toCell())
            .build();

    contract.send(config);
    contract.waitForBalanceChangeWithTolerance(45, Utils.toNano(0.05));

    BigInteger balance = contract.getBalance();
    if (balance.longValue() > Utils.toNano(0.07).longValue()) {
      log.error("scenario11 failed");
    }
    log.info("FINISHED SCENARIO 11");
  }

  List<Destination> createDummyDestinations(int count) {
    List<Destination> result = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      String dstDummyAddress = Utils.generateRandomAddress(0);

      result.add(
          Destination.builder()
              .bounce(false)
              .address(dstDummyAddress)
              .amount(Utils.toNano(0.0001))
              .build());
    }
    return result;
  }
}
