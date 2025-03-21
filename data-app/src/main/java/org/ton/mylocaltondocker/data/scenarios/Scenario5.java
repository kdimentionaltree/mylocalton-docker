package org.ton.mylocaltondocker.data.scenarios;

import static org.ton.mylocaltondocker.data.controller.StartUpTask.dataHighloadFaucetAddress;

import java.math.BigInteger;
import lombok.extern.slf4j.Slf4j;
import org.ton.java.address.Address;
import org.ton.java.smartcontract.types.WalletV2R2Config;
import org.ton.java.smartcontract.wallet.v2.WalletV2R2;
import org.ton.java.tonlib.Tonlib;
import org.ton.java.utils.Utils;
import org.ton.mylocaltondocker.data.db.DB;

/** to up V2R2 wallet, upload state-init, send back to faucet 0.08 and random address 0.01 */
@Slf4j
public class Scenario5 implements Scenario {

  Tonlib tonlib;

  public Scenario5(Tonlib tonlib) {
    this.tonlib = tonlib;
  }

  public void run() {

    log.info("STARTED SCENARIO 5");
    WalletV2R2 contract = WalletV2R2.builder().tonlib(tonlib).build();

    String nonBounceableAddress = contract.getAddress().toNonBounceable();
    DB.addRequest(nonBounceableAddress, Utils.toNano(0.1));
    tonlib.waitForBalanceChange(contract.getAddress(), 60);
    contract.deploy();
    contract.waitForDeployment();

    WalletV2R2Config config =
        WalletV2R2Config.builder()
            .bounce(false)
            .seqno(1)
            .destination1(dataHighloadFaucetAddress)
            .destination2(Address.of(Utils.generateRandomAddress(-1)))
            .amount1(Utils.toNano(0.08))
            .amount2(Utils.toNano(0.01))
            .comment("mlt-scenario5")
            .build();

    contract.send(config);
    contract.waitForBalanceChangeWithTolerance(30, Utils.toNano(0.05));

    BigInteger balance = contract.getBalance();

    if (balance.longValue() > Utils.toNano(0.02).longValue()) {
      log.error("scenario5 failed");
    }

    log.info("FINISHED SCENARIO 5");
  }
}
