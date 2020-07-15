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
import org.apache.dubbo.config.*;
import org.apache.dubbo.config.bootstrap.DubboBootstrap;
import org.apache.dubbo.demo.DemoService;
import org.apache.dubbo.registry.Registry;
import org.apache.dubbo.registry.RegistryFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
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
        /**
         * 设置token使能
         * 设置为true会自动加载token过滤器
         * @see org.apache.dubbo.rpc.filter.TokenFilter
         */
        service.setToken(true);


        Map<String, String> params = new HashMap<>();
        /**
         * 设置限流器
         * 需要在resources文件下配置META-INF.dubbo/internal/org.apache.dubbo.rpc.Filter
         * 配置内容是：tps=org.apache.dubbo.rpc.filter.TpsLimitFilter
         * 参考网址 https://blog.csdn.net/zwjyyy1203/article/details/92775226
         * 参考网址 https://blog.csdn.net/Andyzhu_2005/article/details/83997806
         * 网址参考 https://www.cnblogs.com/luozhiyun/p/10960593.html
         * @see org.apache.dubbo.rpc.filter.TpsLimitFilter
         */
        params.put("tps", "5");
        params.put("tps.interval","1000");
        service.setParameters(params);
        service.setFilter("tps");


        // 方法1：注册中心配置
        {
            RegistryFactory registryFactory = ExtensionLoader.getExtensionLoader(RegistryFactory.class).getAdaptiveExtension();
            Registry registry = registryFactory.getRegistry(URL.valueOf("zookeeper://127.0.0.1:2181"));
            registry.register(URL.valueOf("condition://0.0.0.0/org.apache.dubbo.demo.DemoService?category=routers&dynamic=false&rule=" +
                    URL.encode("host = 127.0.0.1 => host = 127.0.0.1")));
        }

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
