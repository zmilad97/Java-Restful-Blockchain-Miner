package com.github.zmilad97.miner.Service;

import com.github.zmilad97.miner.Module.*;
import com.google.gson.Gson;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.MalformedInputException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

@Service
public class MinerService {

    private int nonce;


    public Block findBlock(Config config) {
        config.coreConfig(config);

        final HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .build();
        String address = config.getBlockChainCoreAddress()+"/block";
        HttpRequest request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(address))
                .setHeader("User-Agent", "Miner")
                .build();

        HttpResponse<String> response = null;
        try {

            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        //Converting response to Block Object
        Gson gson = new Gson();
        Block block = gson.fromJson(response.body(), Block.class);
        //renew Configs
        config.coreConfig(config);

        //add reward transaction to transaction list
        Transaction rewardTransaction = new Transaction();
        rewardTransaction.setTransactionId("1");
        rewardTransaction.setSource(null);
        rewardTransaction.setDestination(config.getWalletPublicId());
        rewardTransaction.setAmount(config.getReward());

        block.addTransaction(rewardTransaction);
        return block;
    }


    public Block mine(Block block, Config config) {
        //Renew Configs
        config.coreConfig(config);

        computeHash(config.getDifficultyLevel(), block);
        return block;
    }

    //Sends block to CoreService to confirm mining
    public Block sendBlock(Block block, Config config) {

        try {

            CloseableHttpClient httpClient = HttpClientBuilder.create().build();
            HttpPost httpPost = new HttpPost(config.getBlockChainCoreAddress() + "/pow");
            Gson gson = new Gson();

            StringEntity params = new StringEntity(gson.toJson(block));
            httpPost.addHeader("Content-Type", "application/json");
            httpPost.setEntity(params);
            CloseableHttpResponse httpResponse = httpClient.execute(httpPost);
            System.out.println(httpResponse.getEntity().getContent());
        } catch (IOException e) {
            e.printStackTrace();
            e.printStackTrace();
        }


        return block;
    }


//    public Boolean mineStatus(Response response, Config config) {
//
//        try {
//
//            String body = "#" + response.getHash() + "#" + response.getNonce() + "#" + response.getRewardTransaction()
//                    .getTransactionId() + "#" + response.getRewardTransaction().getSource() + "#"
//                    + response.getRewardTransaction().getDestination() + "#" + response.getRewardTransaction().getAmount() + "#";
//
//            HttpClient httpClient = HttpClient.newHttpClient();
//            HttpRequest httpRequest = HttpRequest.newBuilder().uri(URI.create(config.getBlockChainCoreAddress() + "/testpost"))
//                    .POST(HttpRequest.BodyPublishers.ofString(body))
//                    .build();
//
//            HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
//
//            System.out.println(httpResponse);
//            return true;
//        } catch (IOException e) {
//            e.printStackTrace();
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//
//        return false;
//    }

    private void computeHash(String difficultyLevel, @NotNull Block block) {
        String hash = "null";

        nonce = -1;

        String transactionStringToHash = "";
        for (int i = 0; i < block.getTransactions().size(); i++)
            transactionStringToHash += block.getTransactions().get(i).getTransactionHash();
        try {
            do {
                nonce++;

                String stringToHash = nonce + block.getIndex() + block.getPreviousHash() + transactionStringToHash;
                Cryptography cryptography = new Cryptography();

                hash = cryptography.toHexString(cryptography.getSha(stringToHash));

                if (hash.startsWith(difficultyLevel)) {
                    System.out.println(stringToHash);
                    break;
                }
            } while (true);

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        block.setNonce(nonce);
        block.setHash(hash);

    }

    public List<Transaction> getTransactionList() {
        List<Transaction> currentTransactions = new ArrayList<>();
        Gson gson = new Gson();

        try {

            URL url = new URL("http://localhost:8080/trx/current");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");

            if (connection.getResponseCode() != 200) {
                throw new RuntimeException("Failed , http error code : " + connection.getResponseCode());
            }

            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(connection.getInputStream()));

            StringBuilder sb = new StringBuilder();
            int cb;
            while ((cb = bufferedReader.read()) != -1) {
                sb.append((char) cb);
            }

            //fixing Strings
            sb.deleteCharAt(sb.length() - 1);
            sb.deleteCharAt(0);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append('"');
            stringBuilder.append("transactionId");

            String[] strings = sb.toString().split(stringBuilder.toString());

            //converting to json
            for (int i = 1; i < strings.length; i++) {
                StringBuilder strb = new StringBuilder();
                strb.append('{');
                strb.append('"');
                strb.append("transactionId");
                strb.append(strings[i]);

                if (i != strings.length - 1) {
                    strb.deleteCharAt(strb.length() - 1);
                    strb.deleteCharAt(strb.length() - 1);
                }

                Transaction transaction = gson.fromJson(strb.toString(), Transaction.class);
                currentTransactions.add(transaction);

            }


//            sb.deleteCharAt(sb.length() - 1);
//            sb.deleteCharAt(0);

            return currentTransactions;

        } catch (MalformedInputException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    public List<Block> getBlocks() {
        List<Block> currentBlocks = new ArrayList<>();
        Gson gson = new Gson();

        try {

            URL url = new URL("http://localhost:8080/chain");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");

            if (connection.getResponseCode() != 200) {
                throw new RuntimeException("Failed , http error code : " + connection.getResponseCode());
            }
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(connection.getInputStream()));

            StringBuilder sb = new StringBuilder();
            int cb;
            while ((cb = bufferedReader.read()) != -1) {
                sb.append((char) cb);
            }

            //fixing Strings
            sb.deleteCharAt(sb.length() - 1);
            sb.deleteCharAt(0);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append('"');
            stringBuilder.append("index");

            String[] strings = sb.toString().split(stringBuilder.toString());
            //converting to json
            for (int i = 1; i < strings.length; i++) {
                StringBuilder strb = new StringBuilder();
                strb.append('{');
                strb.append('"');
                strb.append("index");
                strb.append(strings[i]);
                if (i != strings.length - 1) {
                    strb.deleteCharAt(strb.length() - 1);
                    strb.deleteCharAt(strb.length() - 1);
                }

                Block block = gson.fromJson(strb.toString(), Block.class);
                currentBlocks.add(block);

            }


            sb.deleteCharAt(sb.length() - 1);
            sb.deleteCharAt(0);


            return currentBlocks;

        } catch (MalformedInputException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }


}
