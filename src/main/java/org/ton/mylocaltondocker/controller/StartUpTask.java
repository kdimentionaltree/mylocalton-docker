package org.ton.mylocaltondocker.controller;

import com.iwebpp.crypto.TweetNaclFast;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.ton.java.smartcontract.highload.HighloadWallet;
import org.ton.java.smartcontract.types.Destination;
import org.ton.java.smartcontract.types.HighloadConfig;
import org.ton.java.tonlib.Tonlib;
import org.ton.java.tonlib.types.ExtMessageInfo;
import org.ton.java.utils.Utils;
import org.ton.mylocaltondocker.Main;
import org.ton.mylocaltondocker.db.DB;
import org.ton.mylocaltondocker.db.WalletEntity;
import org.ton.mylocaltondocker.db.WalletPk;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.nonNull;

@Component
@Slf4j
public class StartUpTask {

  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady() throws InterruptedException {
    System.out.println("Web Faucet has started. Sleep 120 seconds till blockchain is ready...");

    Thread.sleep(120 * 1000);
    System.out.println("Initializing tonlib");

    while (!Files.exists(Paths.get("/var/ton-work/db/global.config.json"))) {
      System.out.println("faucet is waiting for /var/ton-work/db/global.config.json");
      Thread.sleep(5000);
    }

    Main.tonlib =
        Tonlib.builder()
            .pathToTonlibSharedLib("/usr/share/data/libtonlibjson.so")
            .pathToGlobalConfig("/var/ton-work/db/global.config.json")
            //            .pathToTonlibSharedLib("g:/libs/master-tonlibjson.dll")
            //            .pathToGlobalConfig("g:/libs/global.config-mlt.json")
            .ignoreCache(false)
            .receiveRetryTimes(10)
            .build();

    byte[] prvKey =
        Utils.hexToSignedBytes("ee26cd8f2709404b63bc172148ec6179bfc7049b1045a22c3ea5446c5d425347");
    TweetNaclFast.Signature.KeyPair keyPair = Utils.generateSignatureKeyPairFromSeed(prvKey);

    HighloadWallet highloadFaucet =
        HighloadWallet.builder()
            .tonlib(Main.tonlib)
            .keyPair(keyPair)
            .wc(-1)
            .walletId(42L)
            .queryId(BigInteger.ZERO)
            .build();

    log.info(
        "faucet address {}, balance {}",
        highloadFaucet.getAddress().toRaw(),
        Utils.formatNanoValue(highloadFaucet.getBalance()));

    log.info(
        "faucet pending queue size {}, total queue size {}",
        DB.getWalletsToSend().size(),
        DB.getTotalWallets());

    log.info(
        "FAUCET_REQUEST_EXPIRATION_PERIOD {}",
        Integer.parseInt(System.getenv("FAUCET_REQUEST_EXPIRATION_PERIOD").strip()));
    log.info(
        "FAUCET_SINGLE_GIVEAWAY {}", Integer.parseInt(System.getenv("FAUCET_SINGLE_GIVEAWAY").strip()));

    runFaucetSendScheduler();
    runFaucetCleanQueueScheduler();

    log.info("faucet ready");
  }

  public void runFaucetSendScheduler() {

    Executors.newSingleThreadScheduledExecutor()
        .scheduleAtFixedRate(
            () -> {
              Thread.currentThread().setName("Send");

              List<WalletEntity> walletRequests = DB.getWalletsToSend();
              log.info("triggered send to {} wallets", walletRequests.size());
              if (walletRequests.size() == 0) {
                log.info("queue is empty, nothing to do, was sent {}", DB.getWalletsSent().size());
                return;
              }

              List<Destination> destinations = new ArrayList<>();
              for (WalletEntity wallet : walletRequests) {
                destinations.add(
                    Destination.builder()
                        .bounce(false)
                        .address(wallet.getWalletAddress())
                        .amount(
                            Utils.toNano(Integer.parseInt(System.getenv("FAUCET_SINGLE_GIVEAWAY").strip())))
                        .build());
              }

              log.info("generated total destinations {}", destinations.size());

              byte[] prvKey =
                  Utils.hexToSignedBytes(
                      "ee26cd8f2709404b63bc172148ec6179bfc7049b1045a22c3ea5446c5d425347");
              TweetNaclFast.Signature.KeyPair keyPair =
                  Utils.generateSignatureKeyPairFromSeed(prvKey);

              HighloadWallet highloadFaucet =
                  HighloadWallet.builder()
                      .tonlib(Main.tonlib)
                      .keyPair(keyPair)
                      .wc(-1)
                      .walletId(42L)
                      .queryId(BigInteger.ZERO)
                      .build();

              List<Destination> destinations250 =
                  new ArrayList<>(destinations.subList(0, Math.min(destinations.size(), 250)));
              log.info("sending batch {}", destinations250.size());
              destinations.subList(0, Math.min(destinations.size(), 250)).clear();

              HighloadConfig config =
                  HighloadConfig.builder()
                      .walletId(42)
                      .queryId(BigInteger.valueOf(Instant.now().getEpochSecond() + 60L << 32))
                      .destinations(destinations250)
                      .build();

              ExtMessageInfo extMessageInfo = highloadFaucet.send(config);
              log.info("contract send result {}", extMessageInfo);
              if (nonNull(extMessageInfo.getHash())) {
                for (Destination destination : destinations250) {
                  DB.updateWalletStatus(
                      WalletPk.builder().walletAddress(destination.getAddress()).build(), "sent");
                }
              }

              log.info(
                  "faucet address {}, balance {}",
                  highloadFaucet.getAddress().toRaw(),
                  Utils.formatNanoValue(highloadFaucet.getBalance()));
            },
            30L,
            30L,
            TimeUnit.SECONDS);
  }

  public void runFaucetCleanQueueScheduler() {

    Executors.newSingleThreadScheduledExecutor()
        .scheduleAtFixedRate(
            () -> {
              Thread.currentThread().setName("Clean");
              DB.deleteExpiredWallets(
                  Integer.parseInt(System.getenv("FAUCET_REQUEST_EXPIRATION_PERIOD").strip()));
            },
            1,
            1,
            TimeUnit.MINUTES);
  }
}
