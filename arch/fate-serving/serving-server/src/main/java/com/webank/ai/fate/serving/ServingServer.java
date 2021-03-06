/*
 * Copyright 2019 The FATE Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.webank.ai.fate.serving;

import com.webank.ai.fate.core.network.http.client.HttpClientPool;
import com.webank.ai.fate.serving.manger.ModelManager;
import com.webank.ai.fate.core.network.grpc.client.ClientPool;
import com.webank.ai.fate.serving.service.ModelService;
import com.webank.ai.fate.serving.service.InferenceService;
import com.webank.ai.fate.core.utils.Configuration;
import com.webank.ai.fate.serving.service.ProxyService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.IOException;
import java.util.ArrayList;

import org.apache.commons.cli.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ServingServer {
    private static final Logger LOGGER = LogManager.getLogger();
    private Server server;
    private String confPath;

    public ServingServer(String confPath){
        this.confPath = confPath;
    }

    private void start() throws IOException {
        this.init();

        int port = Integer.parseInt(Configuration.getProperty("port"));
        //TODO: Server custom configuration
        server = ServerBuilder.forPort(port)
                .addService(new InferenceService())
                .addService(new ModelService())
                .addService(new ProxyService())
                .build();
        LOGGER.info("Server started listening on port: {}, use configuration: {}", port, this.confPath);
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                LOGGER.info("*** shutting down gRPC server since JVM is shutting down");
                ServingServer.this.stop();
                LOGGER.info("*** server shut down");
            }
        });
    }

    private void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    private void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    private void init(){
        new Configuration(this.confPath).load();
        new ModelManager();
        this.initClientPool();
        HttpClientPool.initPool();
    }

    private void initClientPool(){
        ArrayList<String> serverAddress = new ArrayList<>();
        serverAddress.add(Configuration.getProperty("proxy"));
        serverAddress.add(Configuration.getProperty("roll"));
        new Thread(new Runnable() {
            @Override
            public void run() {
                ClientPool.init_pool(serverAddress);
            }
        }).start();
        LOGGER.info("Finish init client pool");
    }

    public static void main(String[] args){
        try{
            Options options = new Options();
            Option option = Option.builder("c")
                    .longOpt("config")
                    .argName("file")
                    .hasArg()
                    .numberOfArgs(1)
                    .desc("configuration file")
                    .build();
            options.addOption(option);
            CommandLineParser parser = new DefaultParser();
            CommandLine cmd = parser.parse(options, args);

            ServingServer a = new ServingServer(cmd.getOptionValue("c"));
            a.start();
            a.blockUntilShutdown();
        }
        catch (Exception ex){
            ex.printStackTrace();
        }
    }
}
