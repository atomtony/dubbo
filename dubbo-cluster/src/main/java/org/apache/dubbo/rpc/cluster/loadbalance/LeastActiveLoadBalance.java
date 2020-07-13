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
 * LeastActiveLoadBalance
 * <p>
 * Filter the number of invokers with the least number of active calls and count the weights and quantities of these invokers.
 * If there is only one invoker, use the invoker directly;
 * if there are multiple invokers and the weights are not the same, then random according to the total weight;
 * if there are multiple invokers and the same weight, then randomly called.
 */
//最小活跃数负载均衡
public class LeastActiveLoadBalance extends AbstractLoadBalance {

    public static final String NAME = "leastactive";

    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        // Number of invokers
        // 服务提供者个数
        int length = invokers.size();
        // The least active value of all invokers
        // 最小活跃数
        int leastActive = -1;
        // The number of invokers having the same least active value (leastActive)
        // 相同最小活跃数的服务提供者个数
        int leastCount = 0;
        // The index of invokers having the same least active value (leastActive)
        // 最小活跃者服务提供下表
        int[] leastIndexes = new int[length];
        // the weight of every invokers
        // 缓存最小活跃服务提供者权重
        int[] weights = new int[length];
        // The sum of the warmup weights of all the least active invokers
        // 所有最小活跃服务提供者权重总和
        int totalWeight = 0;
        // The weight of the first least active invoker
        // 第一个最小活跃服务提供者权重
        int firstWeight = 0;
        // Every least active invoker has the same weight value?
        // 标记是否所有的最小活跃服务提供者有相同的权重
        boolean sameWeight = true;


        // Filter out all the least active invokers
        // 遍历所有服务提供者
        for (int i = 0; i < length; i++) {
            // 服务提供者
            Invoker<T> invoker = invokers.get(i);
            // Get the active number of the invoker
            // 获取服务提供者活跃数
            int active = RpcStatus.getStatus(invoker.getUrl(), invocation.getMethodName()).getActive();
            // Get the weight of the invoker's configuration. The default value is 100.
            // 服务提供者预热后的权重
            int afterWarmup = getWeight(invoker, invocation);
            // save for later use
            // 服务提供者预热后的权重
            weights[i] = afterWarmup;
            // If it is the first invoker or the active number of the invoker is less than the current least active number
            if (leastActive == -1 || active < leastActive) {
                // Reset the active number of the current invoker to the least active number
                // 记录最小活跃数
                leastActive = active;
                // Reset the number of least active invokers
                // 最小活跃者个数
                leastCount = 1;
                // Put the first least active invoker first in leastIndexes
                // 记录最小活跃者下标
                leastIndexes[0] = i;
                // Reset totalWeight
                // 重置总权重
                totalWeight = afterWarmup;
                // Record the weight the first least active invoker
                // 重新设置地一个最小活跃者权重为预热后的权重
                firstWeight = afterWarmup;
                // Each invoke has the same weight (only one invoker here)
                // 标记最小活跃者有相同的权重
                sameWeight = true;
                // If current invoker's active value equals with leaseActive, then accumulating.
            } else if (active == leastActive) {
                // Record the index of the least active invoker in leastIndexes order
                //记录最小活跃者下标，最小活跃者个数自增1
                leastIndexes[leastCount++] = i;
                // Accumulate the total weight of the least active invoker
                // 累加权重
                totalWeight += afterWarmup;
                // If every invoker has the same weight?
                // 判断最小活跃者有相同的权重
                if (sameWeight && afterWarmup != firstWeight) {
                    sameWeight = false;
                }
            }
        }
        // Choose an invoker from all the least active invokers
        // 如果最小活越者就只有一个，返回这个服务提供者
        if (leastCount == 1) {
            // If we got exactly one invoker having the least active value, return this invoker directly.
            return invokers.get(leastIndexes[0]);
        }
        if (!sameWeight && totalWeight > 0) {
            // If (not every invoker has the same weight & at least one invoker's weight>0), select randomly based on 
            // totalWeight.
            int offsetWeight = ThreadLocalRandom.current().nextInt(totalWeight);
            // Return a invoker based on the random value.
            for (int i = 0; i < leastCount; i++) {
                // 最小活跃者下标
                int leastIndex = leastIndexes[i];
                // 随机总权重减去最小活跃者权重
                offsetWeight -= weights[leastIndex];
                if (offsetWeight < 0) {
                    // 根据下标返回最小活跃者
                    return invokers.get(leastIndex);
                }
            }
        }
        // If all invokers have the same weight value or totalWeight=0, return evenly.
        // 所有的提供者有相同的权重或者权重都为0时，随机返回最小活跃者中的一个。
        return invokers.get(leastIndexes[ThreadLocalRandom.current().nextInt(leastCount)]);
    }
}
