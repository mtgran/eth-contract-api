package org.adridadou.ethereum.smartcontract;

import com.google.common.collect.Lists;
import org.adridadou.exception.EthereumApiException;
import org.ethereum.core.CallTransaction;
import org.ethereum.util.blockchain.SolidityContract;
import rx.Observable;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Created by davidroon on 18.08.16.
 * This code is released under Apache 2 license
 */
public class TestSmartContract implements SmartContract {
    private final SolidityContract contract;

    public TestSmartContract(SolidityContract contract) {
        this.contract = contract;
    }

    @Override
    public Observable<Object[]> callFunction(String methodName, Object... arguments) {
        return Observable.just(contract.callFunction(methodName, arguments).getReturnValues());
    }

    @Override
    public Object[] callConstFunction(String methodName, Object... arguments) {
        return contract.callConstFunction(methodName, arguments);
    }

    @Override
    public List<CallTransaction.Function> getFunctions() {
        try {
            Field field = contract.getClass().getDeclaredField("contract");
            field.setAccessible(true);
            CallTransaction.Contract innerContract = (CallTransaction.Contract) field.get(contract);
            return Lists.newArrayList(innerContract.functions);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new EthereumApiException("error while getting functions list", e);
        }
    }
}
