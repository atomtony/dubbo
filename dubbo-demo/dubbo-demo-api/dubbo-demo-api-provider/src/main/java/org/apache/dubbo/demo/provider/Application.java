/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.demo.provider;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;
import org.apache.dubbo.demo.DemoService;
import org.apache.dubbo.registry.Registry;
import org.apache.dubbo.registry.RegistryFactory;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

public class Application {
    public static void main(String[] args) throws Exception {
        if (isClassic(args)) {
            startWithExport();
        } else {
            startWithBootstrap();
        }
    }

    private static boolean isClassic(String[] args) {
        return args.length > 0 && "classic".equalsIgnoreCase(args[0]);
    }

    private static void startWithBootstrap() {
        ServiceConfig<DemoServiceImpl> service = new ServiceConfig<>();
        service.setInterface(DemoService.class);
        service.setRef(new DemoServiceImpl());


        // 方法1：注册中心配置
        RegistryFactory registryFactory = ExtensionLoader.getExtensionLoader(RegistryFactory.class).getAdaptiveExtension();
        Registry registry = registryFactory.getRegistry(URL.valueOf("zookeeper://127.0.0.1:2181"));
        registry.register(URL.valueOf("condition://0.0.0.0:20880/org.apache.dubbo.demo.DemoService?category=routers&dynamic=false&rule=" +
                URL.encode("host = 127.0.0.1 => host = 127.0.0.1")));


        // 方法2：注册中心配置
        RegistryConfig registryConfig1 = new RegistryConfig("zookeeper://127.0.0.1:2181");


        DubboBootstrap bootstrap = DubboBootstrap.getInstance();
        ProtocolConfig protocolConfig = new ProtocolConfig();
        protocolConfig.setPort(20880);
        bootstrap.protocol(protocolConfig);
        bootstrap.application(new ApplicationConfig("dubbo-demo-api-provider"))
                .registries(Arrays.asList(registryConfig1))
//                .registry(new RegistryConfig("zookeeper://127.0.0.1:2182"))
//                .registry(new RegistryConfig("zookeeper://127.0.0.1:2183"))
                .service(service)
                .start()
                .await();
    }

    private static void startWithExport() throws InterruptedException {
        ServiceConfig<DemoServiceImpl> service = new ServiceConfig<>();
        service.setInterface(DemoService.class);
        service.setRef(new DemoServiceImpl());
        service.setApplication(new ApplicationConfig("dubbo-demo-api-provider"));
        service.setRegistry(new RegistryConfig("zookeeper://127.0.0.1:2181"));
        service.export();

        System.out.println("dubbo service started");
        new CountDownLatch(1).await();
    }
}
