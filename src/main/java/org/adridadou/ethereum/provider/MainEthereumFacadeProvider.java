package org.adridadou.ethereum.provider;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.adridadou.ethereum.*;
import org.adridadou.ethereum.handler.EthereumEventHandler;
import org.adridadou.ethereum.handler.OnBlockHandler;
import org.adridadou.ethereum.keystore.FileSecureKey;
import org.adridadou.ethereum.keystore.SecureKey;
import org.adridadou.exception.EthereumApiException;
import org.ethereum.facade.Ethereum;
import org.ethereum.facade.EthereumFactory;

import java.io.File;
import java.util.List;
import java.util.Optional;

/**
 * Created by davidroon on 27.04.16.
 * This code is released under Apache 2 license
 */
public class MainEthereumFacadeProvider implements EthereumFacadeProvider {

    @Override
    public EthereumFacade create() {
        Ethereum ethereum = EthereumFactory.createEthereum();
        EthereumEventHandler ethereumListener = new EthereumEventHandler(ethereum, new OnBlockHandler());
        ethereum.init();

        return new EthereumFacade(new BlockchainProxyReal(ethereum, ethereumListener));
    }

    @Override
    public SecureKey getKey(String id) throws Exception {
        File[] files = new File(getKeystoreFolderPath()).listFiles();

        return Lists.newArrayList(Preconditions.checkNotNull(files, "the folder " + getKeystoreFolderPath() + " cannot be found"))
                .stream().filter(file -> id.equals(file.getName()))
                .findFirst().map(FileSecureKey::new)
                .orElseThrow(() -> new EthereumApiException("the file " + id + " could not be found"));
    }

    @Override
    public List<? extends SecureKey> listAvailableKeys() {
        File[] files = Optional.ofNullable(new File(getKeystoreFolderPath()).listFiles()).orElseThrow(() -> new EthereumApiException("cannot find the folder " + getKeystoreFolderPath()));
        return javaslang.collection.List
                .of(files)
                .filter(File::isFile)
                .map(FileSecureKey::new)
                .toJavaList();
    }

    private String getKeystoreFolderPath() {
        String homeDir = System.getProperty("user.home");
        return homeDir + "/Library/Ethereum/keystore/";
    }
}
