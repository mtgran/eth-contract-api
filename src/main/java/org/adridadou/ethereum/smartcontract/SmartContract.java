package org.adridadou.ethereum.smartcontract;

import org.ethereum.core.CallTransaction;
import rx.Observable;

import java.util.List;

/**
 * Created by davidroon on 18.08.16.
 * This code is released under Apache 2 license
 */
public interface SmartContract {
    Observable<Object[]> callFunction(String methodName, Object... arguments);

    Object[] callConstFunction(String methodName, Object... arguments);

    List<CallTransaction.Function> getFunctions();
}
