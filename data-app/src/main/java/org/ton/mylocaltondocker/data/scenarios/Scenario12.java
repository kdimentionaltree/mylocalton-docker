package org.ton.mylocaltondocker.data.scenarios;

import com.iwebpp.crypto.TweetNaclFast;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.ton.java.cell.Cell;
import org.ton.java.cell.CellBuilder;
import org.ton.java.smartcontract.LibraryDeployer;
import org.ton.java.smartcontract.types.Destination;
import org.ton.java.smartcontract.types.WalletCodes;
import org.ton.java.smartcontract.types.WalletV5Config;
import org.ton.java.smartcontract.wallet.v5.WalletV5;
import org.ton.java.tonlib.Tonlib;
import org.ton.java.utils.Utils;
import org.ton.mylocaltondocker.data.db.DB;

/** deploy V5R1 as library and do a transfer to 255 random recipients */
@Slf4j
public class Scenario12 implements Scenario {
  Tonlib tonlib;

  public Scenario12(Tonlib tonlib) {
    this.tonlib = tonlib;
  }

  public void run() throws NoSuchAlgorithmException {
    log.info("STARTED SCENARIO 12");

    Cell walletV5Code = CellBuilder.beginCell().fromBoc(WalletCodes.V5R1.getValue()).endCell();

    LibraryDeployer libraryDeployer =
        LibraryDeployer.builder().tonlib(tonlib).libraryCode(walletV5Code).build();

    if (!tonlib.isDeployed(libraryDeployer.getAddress())) {
      String nonBounceableAddressLib = libraryDeployer.getAddress().toNonBounceable();
      log.info("nonBounceable addressLib {}", nonBounceableAddressLib);
      log.info("raw addressLib {}", libraryDeployer.getAddress().toRaw());

      DB.addRequest(nonBounceableAddressLib, Utils.toNano(1));
      Utils.sleep(30, "wait for lib balance change");
      libraryDeployer.deploy();
      Utils.sleep(30, "wait for lib to be deployed");
    }

    // deploy V5R1 as library
    TweetNaclFast.Signature.KeyPair keyPair = Utils.generateSignatureKeyPair();
    long walletId = Math.abs(Utils.getRandomInt());

    WalletV5 contract =
        WalletV5.builder()
            .tonlib(tonlib)
            .walletId(walletId)
            .keyPair(keyPair)
            .isSigAuthAllowed(true)
            .deployAsLibrary(true)
            .build();

    String nonBounceableAddress = contract.getAddress().toNonBounceable();
    log.info("v5 address {}", nonBounceableAddress);
    DB.addRequest(nonBounceableAddress, Utils.toNano(1.5));
    tonlib.waitForBalanceChange(contract.getAddress(), 60);

    contract.deploy();
    contract.waitForDeployment();
    Utils.sleep(5);

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
      log.error("scenario12 failed");
    }
    log.info("FINISHED SCENARIO 12");
  }

  List<Destination> createDummyDestinations(int count) {
    List<Destination> result = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      String dstDummyAddress = Utils.generateRandomAddress(-1);

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
