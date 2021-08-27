/*
 * Copyright 2018 blitzpay
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lnvault;

import com.lnvault.repository.Repository;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.logging.Logger;
import net.milkbowl.vault.economy.Economy;

public class Context {
    
    private Logger logger;
    private Economy economy;
    private Repository Repo;
    private LnBackend lnBackend;
    private BlockingQueue<Runnable> MinecraftQueue;
    private ScheduledThreadPoolExecutor BackgroundQueue;    
    
    private volatile boolean paymentRequestActive;
    private volatile boolean withdrawalActive;
    private volatile long paymentRequestTimeStamp;

    public Context(Logger logger, Economy economy , Repository repo, LnBackend lnBackend)
    {
        this.logger=logger;
        this.economy = economy;
        this.MinecraftQueue = new LinkedBlockingQueue<Runnable>();
        this.BackgroundQueue = new ScheduledThreadPoolExecutor(1);
        this.Repo = repo;
        this.lnBackend = lnBackend;
    }

    public Logger getLogger() {
        return logger;
    }

    public Economy getEconomy() {
        return economy;
    }

    public Repository getRepo() {
        return Repo;
    }

    public LnBackend getLnBackend() {
        return lnBackend;
    }

    public BlockingQueue<Runnable> getMinecraftQueue() {
        return MinecraftQueue;
    }

    public ScheduledThreadPoolExecutor getBackgroundQueue() {
        return BackgroundQueue;
    }

    public boolean isPaymentRequestActive() {
        return paymentRequestActive;
    }
    
    public long getMostRecentPaymentRequestTimeStamp() {
        return paymentRequestTimeStamp;
    }

    public void setPaymentRequestActive(boolean paymentRequestActive , long timeStamp) {
        this.paymentRequestActive = paymentRequestActive;
        this.paymentRequestTimeStamp = timeStamp;
    }

    public boolean isWithdrawalActive() {
        return withdrawalActive;
    }

    public void setWithdrawalActive(boolean withdrawalActive) {
        this.withdrawalActive = withdrawalActive;
    }
    
}
