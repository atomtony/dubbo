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
package org.apache.dubbo.rpc.cluster.loadbalance;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcStatus;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ShortestResponseLoadBalance
 * </p>
 * Filter the number of invokers with the shortest response time of success calls and count the weights and quantities of these invokers.
 * If there is only one invoker, use the invoker directly;
 * if there are multiple invokers and the weights are not the same, then random according to the total weight;
 * if there are multiple invokers and the same weight, then randomly called.
 */
// 最短响应时间负载均衡
public class ShortestResponseLoadBalance extends AbstractLoadBalance {

    public static final String NAME = "shortestresponse";

    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        // Number of invokers
        // 提供者个数
        int length = invokers.size();
        // Estimated shortest response time of all invokers
        // 初始化最短响应事件
        long shortestResponse = Long.MAX_VALUE;
        // The number of invokers having the same estimated shortest response time
        // 最短响应的提供者个数
        int shortestCount = 0;
        // The index of invokers having the same estimated shortest response time
        // 最短响应提供者索引
        int[] shortestIndexes = new int[length];
        // the weight of every invokers
        // 权重值
        int[] weights = new int[length];
        // The sum of the warmup weights of all the shortest response  invokers
        // 权重总和
        int totalWeight = 0;
        // The weight of the first shortest response invokers
        // 第一个提供者权重
        int firstWeight = 0;
        // Every shortest response invoker has the same weight value?
        // 最短响应提供者权重相同
        boolean sameWeight = true;

        // Filter out all the shortest response invokers
        for (int i = 0; i < length; i++) {
            Invoker<T> invoker = invokers.get(i);
            // 获取响应RPC状态
            RpcStatus rpcStatus = RpcStatus.getStatus(invoker.getUrl(), invocation.getMethodName());
            // Calculate the estimated response time from the product of active connections and succeeded average elapsed time.
            // 平均相应时间
            long succeededAverageElapsed = rpcStatus.getSucceededAverageElapsed();
            // 提供者活跃数
            int active = rpcStatus.getActive();
            // 平均时间 * 活跃数
            long estimateResponse = succeededAverageElapsed * active;
            // 提供者权重
            int afterWarmup = getWeight(invoker, invocation);
            // 缓存权重
            weights[i] = afterWarmup;
            // Same as LeastActiveLoadBalance
            if (estimateResponse < shortestResponse) {
                // 保存最小相应时间
                shortestResponse = estimateResponse;
                // 最小相应个数
                shortestCount = 1;
                // 最小相应提供者下表
                shortestIndexes[0] = i;
                // 重置权重总和、重置第一个提供者权重
                totalWeight = afterWarmup;
                firstWeight = afterWarmup;
                sameWeight = true;
            } else if (estimateResponse == shortestResponse) {
                // 最小相应提供者下表,自增最小响应个数
                shortestIndexes[shortestCount++] = i;
                // 累加权重
                totalWeight += afterWarmup;
                // 记录权重是否相同
                if (sameWeight && i > 0
                        && afterWarmup != firstWeight) {
                    sameWeight = false;
                }
            }
        }
        // 最小响应提供者只有一个，根据下标返回
        if (shortestCount == 1) {
            return invokers.get(shortestIndexes[0]);
        }
        // 权重不相同，且总权重大于0,那么就从最小响应提供者中随机选择一个
        if (!sameWeight && totalWeight > 0) {
            int offsetWeight = ThreadLocalRandom.current().nextInt(totalWeight);
            for (int i = 0; i < shortestCount; i++) {
                int shortestIndex = shortestIndexes[i];
                offsetWeight -= weights[shortestIndex];
                if (offsetWeight < 0) {
                    return invokers.get(shortestIndex);
                }
            }
        }
        // 从最小响应提供者中随机选择一个
        return invokers.get(shortestIndexes[ThreadLocalRandom.current().nextInt(shortestCount)]);
    }
}
