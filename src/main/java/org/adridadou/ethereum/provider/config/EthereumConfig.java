package org.adridadou.ethereum.provider.config;

/**
 * Created by davidroon on 04.08.16.
 * This code is released under Apache 2 license
 */


import java.util.ArrayList;
import java.util.List;

/**
 * "peer.discovery = {
 * "+
 * "    # List of the peers to start
 * "    # the search of the online peers
 * "    # values: [ip:port, ip:port, ip:port ...]
 * "    ip.list = [
 * "        \"94.242.229.4:40404\",
 * "        \"94.242.229.203:30303\"
 * "    ]
 * "}
 * "
 * "# Network id
 * "peer.networkId = 2
 * "
 * "# Enable EIP-8
 * "peer.p2p.eip8 = true
 * "
 * "# the folder resources/genesis
 * "# contains several versions of
 * "# genesis configuration according
 * "# to the network the peer will run on
 * "genesis = frontier-morden.json
 * "
 * "# Blockchain settings (constants and algorithms) which are
 * "# not described in the genesis file (like MINIMUM_DIFFICULTY or Mining algorithm)
 * "blockchain.config.name = \"morden\"
 * "
 * "database {
 * "    # place to save physical storage files
 * "    dir = database-morden
 * "}
 */
public class EthereumConfig {
    private final List<String> ipfs = new ArrayList<>();
    private long networkId;
    private boolean eip8Enabled;
    private String genesis;
    private String blockchainConfigName;
    private String databaseDir;

}