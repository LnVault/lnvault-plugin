/*
 * Copyright 2021 LnVault.
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

import com.lnvault.data.PaymentRequest;
import com.lnvault.data.WithdrawalRequest;
import java.util.function.Function;
import org.bukkit.entity.Player;

public interface LnBackend {
    
    public void generatePaymentRequest(Player player, long satsAmount , double localAmount, Function<PaymentRequest,?> generated, Function<Exception,?> fail ,Function<PaymentRequest,?> confirmed);
    
    public void generateWithdrawal(Player player, long satsAmount, double localAmount, Function<WithdrawalRequest,?> generated, Function<Exception,?> fail,Function<WithdrawalRequest,?> confirmed);
}
