package org.adridadou.ethereum;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.adridadou.ethereum.converters.*;
import org.adridadou.ethereum.smartcontract.SmartContract;
import org.adridadou.exception.ContractNotFoundException;
import org.adridadou.exception.EthereumApiException;
import org.ethereum.core.CallTransaction;
import org.ethereum.crypto.ECKey;
import org.ethereum.solidity.compiler.CompilationResult;
import org.ethereum.solidity.compiler.SolidityCompiler;
import rx.Observable;

import java.io.IOException;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by davidroon on 31.03.16.
 * This code is released under Apache 2 license
 */
public class EthereumContractInvocationHandler implements InvocationHandler {

    private final Map<String, SmartContract> contracts = Maps.newHashMap();
    private final BlockchainProxy blockchainProxy;
    private final List<TypeHandler<?>> handlers;

    EthereumContractInvocationHandler(BlockchainProxy blockchainProxy) {
        this.blockchainProxy = blockchainProxy;
        handlers = Lists.newArrayList(
                new IntegerHandler(),
                new LongHandler(),
                new StringHandler(),
                new BooleanHandler(),
                new AddressHandler()
        );
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        final String contractName = method.getDeclaringClass().getSimpleName().toLowerCase();
        final String methodName = method.getName();
        SmartContract contract = contracts.get(contractName);
        Object[] arguments = args == null ? new Object[0] : args;
        if (method.getReturnType().equals(Void.TYPE)) {
            contract.callFunction(methodName, arguments);
            return Void.TYPE;
        } else {
            if (method.getReturnType().equals(Observable.class)) {
                return contract.callFunction(methodName, arguments).map(result -> convertResult(result, method));
            } else {
                return convertResult(contract.callConstFunction(methodName, arguments), method);
            }
        }
    }

    private Object convertResult(Object[] result, Method method) {
        if (result.length == 1) {
            return convertResult(result[0], method.getReturnType(), method.getGenericReturnType());
        }

        return convertSpecificType(result, method.getReturnType());
    }

    private Object convertSpecificType(Object[] result, Class<?> returnType) {
        Object[] params = new Object[result.length];

        Constructor constr = lookForNonEmptyConstructor(returnType, result);

        for (int i = 0; i < result.length; i++) {
            params[i] = convertResult(result[i], constr.getParameterTypes()[i], constr.getGenericParameterTypes()[i]);
        }


        try {
            return constr.newInstance(params);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new EthereumApiException("error while converting to a specific type", e);
        }
    }

    private Class<?> getCollectionType(Class<?> returnType, Type genericType) {
        if (returnType.isArray()) {
            return returnType.getComponentType();
        }
        if (List.class.equals(returnType)) {
            return getGenericType(genericType);
        }
        return null;
    }

    private Class<?> getGenericType(Type genericType) {
        return (Class<?>) ((ParameterizedType) genericType).getActualTypeArguments()[0];
    }

    private <T> T[] convertArray(Class<T> cls, Object[] arr) {
        for (TypeHandler<?> handler : handlers) {
            if (handler.isOfType(cls)) {
                T[] result = (T[]) Array.newInstance(cls, arr.length);
                for (int i = 0; i < arr.length; i++) {
                    result[i] = (T) handler.convert(arr[i]);
                }
                return result;
            }
        }
        throw new IllegalArgumentException("no handler founds to convert " + cls.getSimpleName());
    }

    private <T> List<T> convertList(Class<T> cls, Object[] arr) {
        for (TypeHandler<?> handler : handlers) {
            if (handler.isOfType(cls)) {
                List<T> result = new ArrayList<>();
                for (Object obj : arr) {
                    result.add((T) handler.convert(obj));
                }
                return result;
            }
        }
        throw new IllegalArgumentException("no handler founds to convert " + cls.getSimpleName());
    }

    private Object convertResult(Object result, Class<?> returnType, Type genericType) {
        Class<?> arrType = getCollectionType(returnType, genericType);
        Class<?> actualReturnType = returnType;
        if (arrType != null) {
            if (returnType.isArray()) {
                return convertArray(arrType, (Object[]) result);
            }

            return convertList(arrType, (Object[]) result);
        }

        if (returnType.equals(Observable.class)) {
            actualReturnType = getGenericType(genericType);
        }

        for (TypeHandler<?> handler : handlers) {
            if (handler.isOfType(actualReturnType)) {
                return handler.convert(result);
            }
        }

        return convertSpecificType(new Object[]{result}, returnType);
    }

    private Constructor lookForNonEmptyConstructor(Class<?> returnType, Object[] result) {
        for (Constructor constructor : returnType.getConstructors()) {
            if (constructor.getParameterCount() > 0) {
                if (constructor.getParameterCount() != result.length) {
                    throw new IllegalArgumentException("the number of arguments don't match for type " + returnType.getSimpleName() + ". Constructor has " + constructor.getParameterCount() + " and result has " + result.length);
                }
                return constructor;
            }
        }

        throw new IllegalArgumentException("no constructor with arguments found! for type " + returnType.getSimpleName());
    }

    void register(Class<?> contractInterface, String code, String contractName, EthAddress address, ECKey sender) throws IOException {
        if (contracts.containsKey(contractInterface.getSimpleName())) {
            throw new EthereumApiException("attempt to register " + contractInterface.getSimpleName() + " twice!");
        }
        final Map<String, CompilationResult.ContractMetadata> contractsFound = compile(code).contracts;
        CompilationResult.ContractMetadata found = null;
        for (Map.Entry<String, CompilationResult.ContractMetadata> entry : contractsFound.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(contractInterface.getSimpleName())) {
                if (found != null) {
                    throw new EthereumApiException("more than one Contract found for " + contractInterface.getSimpleName());
                }
                found = entry.getValue();
            }
        }
        if (found == null) {
            throw new ContractNotFoundException("no contract found for " + contractInterface.getSimpleName());
        }
        SmartContract smartContract = blockchainProxy.map(code, contractName, address, sender);

        verifyContract(smartContract, contractInterface);

        contracts.put(contractInterface.getSimpleName().toLowerCase(), smartContract);
    }

    private void verifyContract(SmartContract smartContract, Class<?> contractInterface) {
        Set<Method> interfaceMethods = Sets.newHashSet(contractInterface.getMethods());
        Set<CallTransaction.Function> solidityMethods = smartContract.getFunctions().stream().filter(f -> f != null).collect(Collectors.toSet());

        Set<String> interfaceMethodNames = interfaceMethods.stream().map(Method::getName).collect(Collectors.toSet());
        Set<String> solidityFuncNames = solidityMethods.stream().map(d -> d.name).collect(Collectors.toSet());

        Sets.SetView<String> superfluous = Sets.difference(interfaceMethodNames, solidityFuncNames);

        if (!superfluous.isEmpty()) {
            throw new EthereumApiException("superflous function definition in interface " + contractInterface.getName() + ":" + superfluous.toString());
        }

        Map<String, Method> methods = interfaceMethods.stream().collect(Collectors.toMap(Method::getName, Function.identity()));

        for (CallTransaction.Function func : solidityMethods) {
            if (methods.get(func.name) != null && func.inputs.length != methods.get(func.name).getParameterCount()) {
                throw new EthereumApiException("parameter count mismatch for " + func.name + " on contract " + contractInterface.getName());
            }
        }

    }

    private CompilationResult compile(final String contract) throws IOException {
        SolidityCompiler.Result res = SolidityCompiler.compile(
                contract.getBytes(EthereumFacade.CHARSET), true, SolidityCompiler.Options.ABI, SolidityCompiler.Options.BIN, SolidityCompiler.Options.INTERFACE);


        return CompilationResult.parse(res.output);
    }
}
