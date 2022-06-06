package org.lsd.alien.pool;

import java.sql.Connection;
import java.util.Timer;

/**
 * @author nhsoft.lsd
 */
public class AlienConnectionHolder {

    protected final Connection conn;
    protected final AlienDataSource dataSource;


    protected final long                          connectTimeMillis;
    protected volatile long                       lastActiveTimeMillis;
    protected volatile long                       lastExecTimeMillis;
    protected volatile long                       lastKeepTimeMillis;
    protected volatile long                       lastValidTimeMillis;

    protected volatile boolean active;

    public AlienConnectionHolder(final Connection conn, final AlienDataSource dataSource) {
        this.conn = conn;
        this.dataSource = dataSource;
        connectTimeMillis = System.currentTimeMillis();
        this.lastActiveTimeMillis = connectTimeMillis;
    }

    public Connection getConn() {
        return conn;
    }

    public AlienDataSource getDataSource() {
        return dataSource;
    }

    public long getLastExecTimeMillis() {
        return lastExecTimeMillis;
    }

    public void setLastExecTimeMillis(final long lastExecTimeMillis) {
        this.lastExecTimeMillis = lastExecTimeMillis;
    }

}
