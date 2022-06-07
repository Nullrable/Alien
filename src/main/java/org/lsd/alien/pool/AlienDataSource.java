package org.lsd.alien.pool;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;
import javax.naming.OperationNotSupportedException;
import javax.sql.ConnectionPoolDataSource;
import javax.sql.DataSource;
import org.lsd.alien.logging.Log;
import org.lsd.alien.logging.LogFactory;
import org.lsd.alien.pool.vendor.MySqlValidConnectionChecker;
import org.lsd.alien.util.JdbcUtils;
import org.lsd.alien.util.MySqlUtils;

/**
 * @author nhsoft.lsd
 */
public class AlienDataSource implements ConnectionPoolDataSource, DataSource {

    private static final Log LOG = LogFactory.getLog(AlienDataSource.class);

    /**
     * 连接池
     */
    private AlienConnectionHolder[] connections;

    /**
     * 需要驱逐的连接
     */
    private AlienConnectionHolder[]  evictConnections;

    /**
     * 需要保活的连接
     */
    private AlienConnectionHolder[]  keepAliveConnections;

    /**
     * 线程池里数据大小
     */
    private int poolingCount = 0;

    /**
     * 活跃数，就是从connections获取后的数量
     */
    private int activeCount = 0;

    public final static int DEFAULT_INITIAL_SIZE = 1;
    public final static int DEFAULT_MAX_ACTIVE_SIZE = 8;
    public final static int DEFAULT_MIN_IDLE = 0;
    public final static int DEFAULT_MAX_WAIT = -1;

    /**
     * 初始化连接数
     */
    private int initialSize = DEFAULT_INITIAL_SIZE;

    /**
     * 是否保活
     */
    private boolean keepAlive;

    /**
     * 最大活跃数
     */
    private int maxActive = DEFAULT_MAX_ACTIVE_SIZE;

    /**
     * 最小空闲数
     */
    private int minIdle = DEFAULT_MIN_IDLE;

    /**
     * 获取连接最大等待时间，-1表示无限等待
     */
    private long maxWait = DEFAULT_MAX_WAIT;

    public static final long                           DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS    = 1000L * 60L * 30L;
    public static final long                           DEFAULT_MAX_EVICTABLE_IDLE_TIME_MILLIS    = 1000L * 60L * 60L * 7;
    public static final long                           DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS = 60 * 1000L;

    //TODO nhsoft.lsd 以下参数应用
    //diff(poolingCount, minIdle) 线程池中至少会保持minIdle个连接
    /**
     * 30分钟，最小可驱逐空闲时间
     */
    protected volatile long                            minEvictableIdleTimeMillis                = DEFAULT_MIN_EVICTABLE_IDLE_TIME_MILLIS;

    /**
     * 7个小时，最大可驱逐空闲时间，这个时间跟数据库本身失效时间相近（8个小时）
     */
    protected volatile long                            maxEvictableIdleTimeMillis                = DEFAULT_MAX_EVICTABLE_IDLE_TIME_MILLIS;

    /**
     * 2分钟，小于这个时间一般不需要放入保活连接池和驱逐连接池
     */
    protected volatile long                            keepAliveBetweenTimeMillis                = DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS * 2;

    /**
     * 1分钟，驱逐时堵塞时间，
     */
    protected volatile long                            timeBetweenEvictionRunsMillis             = DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS;

    public final static boolean                        DEFAULT_TEST_ON_BORROW                    = false;
    public final static boolean                        DEFAULT_TEST_ON_RETURN                    = false;
    public final static boolean                        DEFAULT_WHILE_IDLE                        = true;

    /**
     * 获取数据库连接时，校验连接是否有效
     */
    protected volatile boolean                         testOnBorrow                              = DEFAULT_TEST_ON_BORROW;

    /**
     * 归还数据库连接时，校验连接是否有效
     */
    protected volatile boolean                         testOnReturn                              = DEFAULT_TEST_ON_RETURN;

    /**
     * 获取数据库连接时，如果testOnBorrow=false，连接超过空闲时间大于timeBetweenEvictionRunsMillis时校验连接是否有效
     */
    protected volatile boolean                         testWhileIdle                             = DEFAULT_WHILE_IDLE;

    protected boolean                        checkExecuteTime          = false;
    /**
     * 校验数据库连接是否有效的SQL
     */
    protected volatile String                          validationQuery                           = null;
    protected volatile int                             validationQueryTimeout                    = -1;

    private Driver driver;
    private String driverClassName;
    private String url;
    private String username;
    private String password;

    //安全相关
    private final ReentrantLock lock;
    private final Condition empty;
    private final Condition notEmpty;
    private CountDownLatch latch = new CountDownLatch(2);


    protected PrintWriter logWriter = new PrintWriter(System.out);

    private ValidConnectionChecker validConnectionChecker;

    private boolean inited = false;

    public AlienDataSource() {
        this(false);
    }

    public AlienDataSource(boolean fair) {
        lock = new ReentrantLock(fair);
        empty = lock.newCondition();
        notEmpty = lock.newCondition();

    }

    @Override
    public AlienPooledConnection getConnection() throws SQLException {
        return getConnection(maxWait);
    }

    @Override
    public AlienPooledConnection getConnection(final String username, final String password) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public <T> T unwrap(final Class<T> iface) throws SQLException {
        return null;
    }

    @Override
    public boolean isWrapperFor(final Class<?> iface) throws SQLException {
        return false;
    }

    public void init() throws SQLException {

        if (inited) {
            return;
        }

        if (maxActive <= 0) {
            throw new IllegalArgumentException("illegal maxActive " + maxActive);
        }

        if (maxActive < minIdle) {
            throw new IllegalArgumentException("illegal maxActive " + maxActive);
        }

        if (getInitialSize() > maxActive) {
            throw new IllegalArgumentException("illegal initialSize " + this.initialSize + ", maxActive " + maxActive);
        }

        if (maxEvictableIdleTimeMillis < minEvictableIdleTimeMillis) {
            throw new SQLException("maxEvictableIdleTimeMillis must be grater than minEvictableIdleTimeMillis");
        }

        if (keepAlive && keepAliveBetweenTimeMillis <= timeBetweenEvictionRunsMillis) {
            throw new SQLException("keepAliveBetweenTimeMillis must be grater than timeBetweenEvictionRunsMillis");
        }

        validationQueryCheck();

        /**
         * 1. 初始化Driver
         */
        try {
            if (this.driverClassName != null) {
                this.driverClassName = driverClassName.trim();
            }
            Class.forName(driverClassName);
        } catch (ClassNotFoundException e) {
            throw new SQLException(e.getMessage(), e);
        }

        driver = DriverManager.getDriver(url);

        /**
         * 2. 初始化SQLChecker，这个主要用于校验连接有效性
         */
        initValidConnectionChecker();

        /**
         * 3. 初始化线程池和创建连接
         */
        //size正常可以改为maxActive
        connections = new AlienConnectionHolder[maxActive];
        evictConnections = new AlienConnectionHolder[maxActive];
        keepAliveConnections = new AlienConnectionHolder[maxActive];

        //同步创建初始化线程
        while (poolingCount < (keepAlive ? minIdle : initialSize)) {
            try {
                Connection connection = createPhysicalConnection();
                AlienConnectionHolder holder = new AlienConnectionHolder(connection, this);
                connections[poolingCount++] = holder;
            } catch (SQLException ex) {
                LOG.error("init datasource error, url: " + this.getUrl(), ex);
            }
        }

        /**
         * 4. 开启创建和销毁数据库连接线程
         */
        createAndStartDestroyThread();
        createAndStartCreatorThread();
        try {
            latch.await();
        } catch (InterruptedException e) {
            LOG.error(e.getMessage(), e);
            throw new SQLException(e.getMessage(), e);
        }
        inited = true;
        LOG.info("AlienDataSource inited");
        LOG.info("当前连接数：" + poolingCount);
    }

    private void initValidConnectionChecker()throws SQLException {
        if (this.validConnectionChecker != null) {
            return;
        }

        String realDriverClassName = driver.getClass().getName();
        if (JdbcUtils.isMySqlDriver(realDriverClassName)) {
            this.validConnectionChecker = new MySqlValidConnectionChecker(false);

        } else {
            throw new SQLException(realDriverClassName + " not supported");
        }

    }

    /**
     * 这三个参数（testOnBorrow，testOnReturn，testWhileIdle）是获取和归还时，校验数据库连接有效期的
     */
    private void validationQueryCheck() {
        if (!(testOnBorrow || testOnReturn || testWhileIdle)) {
            return;
        }

        if (this.validConnectionChecker != null) {
            return;
        }

        if (this.validationQuery != null && this.validationQuery.length() > 0) {
            return;
        }

        String errorMessage = "";

        if (testOnBorrow) {
            errorMessage += "testOnBorrow is true, ";
        }

        if (testOnReturn) {
            errorMessage += "testOnReturn is true, ";
        }

        if (testWhileIdle) {
            errorMessage += "testWhileIdle is true, ";
        }

        LOG.error(errorMessage + "validationQuery not set");
    }

    public void recycle(AlienPooledConnection conn) throws SQLException{

        AlienConnectionHolder holder = conn.getHolder();
        if (conn.isClosed()) {

            try {
                lock.lock();
                if (holder.active) {
                    activeCount--;
                    holder.active = false;
                }
            } finally {
                lock.unlock();
            }

            return;
        }

        if (testOnReturn) {
            boolean validate = testConnectionInternal(holder, conn);
            if (!validate) {
                JdbcUtils.close(conn);
                lock.lock();
                try {
                    if (holder.active) {
                        activeCount--;
                        holder.active = false;
                    }
                } finally {
                    lock.unlock();
                }
                return;
            }
        }

        lock.lock();
        try {
            LOG.info("归还前，连接池里连接数为" + poolingCount);

            connections[poolingCount] = holder;
            poolingCount++;

            notEmpty.signalAll();

        } finally {
            lock.unlock();
            LOG.info("归还后，连接池里连接数为" + poolingCount);
        }
    }

    private void createAndStartCreatorThread() {
        String threadName = "Alien-ConnectionPool-Create-" + System.identityHashCode(this);
        Thread thread = new Thread(new CreateConnectionThread());
        thread.setName(threadName);
        thread.setDaemon(true);
        thread.start();
    }

    private void createAndStartDestroyThread() {
        String threadName = "Alien-ConnectionPool-Destroy-" + System.identityHashCode(this);
        Thread thread = new Thread(new DestroyConnectionThread());
        thread.setName(threadName);
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * 销毁数据库连接
     */
    public class DestroyConnectionThread implements Runnable {

        @Override
        public void run() {
            latch.countDown();

            for (;;) {
                // 从前面开始删除
                try {
                    if (timeBetweenEvictionRunsMillis > 0) {
                        Thread.sleep(timeBetweenEvictionRunsMillis);
                    } else {
                        Thread.sleep(1000); //
                    }

                    if (Thread.interrupted()) {
                        break;
                    }
                    shrink(true, keepAlive);
                } catch (InterruptedException e) {
                    break;
                }
            }

        }
    }

    public void shrink(boolean checkTime, boolean keepAlive) {
        try {
            lock.lockInterruptibly();
        } catch (InterruptedException e) {
            return;
        }

        boolean needFill = false;
        int evictCount = 0;
        int keepAliveCount = 0;

        try {
            if (!inited) {
                return;
            }

            final int checkCount = poolingCount - minIdle;
            final long currentTimeMillis = System.currentTimeMillis();
            for (int i = 0; i < poolingCount; ++i) {
                AlienConnectionHolder connection = connections[i];

                if (checkTime) {

                    long idleMillis = currentTimeMillis - connection.lastActiveTimeMillis;

                    if (idleMillis < minEvictableIdleTimeMillis
                            && idleMillis < keepAliveBetweenTimeMillis
                    ) {
                        LOG.info(Thread.currentThread().getName() + " 回收连接空间时间还很短，当前线程池：：" + poolingCount);
                        break;
                    }

                    if (idleMillis >= minEvictableIdleTimeMillis) {
                        if (checkTime && i < checkCount) {
                            evictConnections[evictCount++] = connection;
                            continue;
                        } else if (idleMillis > maxEvictableIdleTimeMillis) {
                            evictConnections[evictCount++] = connection;
                            continue;
                        }
                    }

                    if (keepAlive && idleMillis >= keepAliveBetweenTimeMillis) {
                        keepAliveConnections[keepAliveCount++] = connection;
                    }
                } else {
                    if (i < checkCount) {
                        evictConnections[evictCount++] = connection;
                    } else {
                        break;
                    }
                }
            }

            int removeCount = evictCount + keepAliveCount;
            if (removeCount > 0) {
                System.arraycopy(connections, removeCount, connections, 0, poolingCount - removeCount);
                Arrays.fill(connections, poolingCount - removeCount, poolingCount, null);
                poolingCount -= removeCount;
            }

            if (keepAlive && poolingCount + activeCount < minIdle) {
                needFill = true;
            }
        } finally {
            lock.unlock();
        }

        if (evictCount > 0) {
            for (int i = 0; i < evictCount; ++i) {
                AlienConnectionHolder item = evictConnections[i];
                Connection connection = item.getConn();
                JdbcUtils.close(connection);
            }
            Arrays.fill(evictConnections, null);
        }

        if (keepAliveCount > 0) {
            // keep order
            for (int i = keepAliveCount - 1; i >= 0; --i) {
                AlienConnectionHolder holder = keepAliveConnections[i];
                Connection connection = holder.getConn();

                boolean validate = false;
                try {
                    validate = testConnectionInternal(holder, connection);
                    holder.lastKeepTimeMillis = System.currentTimeMillis();
                } catch (Throwable error) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("keepAliveErr", error);
                    }
                    // skip
                }

                boolean discard = !validate;
                if (validate) {
                    holder.lastKeepTimeMillis = System.currentTimeMillis();

                    lock.lock();
                    try {
                        connections[poolingCount] = holder;
                        poolingCount++;
                        notEmpty.signal();
                    } finally {
                        lock.unlock();
                    }
                }

                if (discard) {
                    try {
                        connection.close();
                    } catch (Exception e) {
                        // skip
                    }

                    lock.lock();
                    try {
                        if (activeCount + poolingCount <= minIdle) {
                            empty.signalAll();
                        }
                    } finally {
                        lock.unlock();
                    }
                }
            }
            Arrays.fill(keepAliveConnections, null);//处理完以后，要将keepAliveConnections置空，以备下次还要用
        }

        if (needFill) {
            lock.lock();
            try {
                int fillCount = minIdle - (activeCount + poolingCount);
                for (int i = 0; i < fillCount; ++i) {
                   empty.signalAll();
                }
            } finally {
                lock.unlock();
            }
        }

        LOG.info("回收连接后，当前连接数：" + poolingCount);
    }

    /**
     * 创建数据库连接
     */
    public class CreateConnectionThread implements Runnable {

        @Override
        public void run() {

            latch.countDown();

            for (; ; ) {

                try {
                    lock.lockInterruptibly();
                } catch (InterruptedException e) {
                    break;
                }

                Connection conn = null;
                try {

                    if (keepAlive) {
                        if (poolingCount < minIdle) {
                            empty.await();
                            continue;
                        }
                    }

                    if (poolingCount >= maxActive) {
                        empty.await();
                        continue;
                    }

                    conn = createPhysicalConnection();
                    AlienConnectionHolder holder = new AlienConnectionHolder(conn, AlienDataSource.this);
                    connections[poolingCount] = holder;
                    poolingCount++;

                    notEmpty.signalAll();

                    LOG.info("创建线程后，当前连接数：" + poolingCount);

                } catch (InterruptedException e) {
                    break;
                } catch (SQLException e) {
                    LOG.error(e.getMessage(), e);
                    JdbcUtils.close(conn);
                } catch (Exception e) {
                    LOG.error(e.getMessage(), e);
                    JdbcUtils.close(conn);
                } finally {
                    lock.unlock();
                }
            }
        }
    }

    private Connection createPhysicalConnection() throws SQLException {
        Properties properties = new Properties();
        if (username != null && username.length() != 0) {
            properties.put("user", username);
        }

        if (password != null && password.length() != 0) {
            properties.put("password", password);
        }
        Connection conn = getDriver().connect(url, properties);
        return conn;
    }

    public Driver getDriver() {
        return driver;
    }

    @Override
    public AlienPooledConnection getPooledConnection() throws SQLException {
        throw new UnsupportedOperationException("Not supported by DruidDataSource");
    }

    public AlienPooledConnection getConnection(long maxWaitMillis) throws SQLException {

        final long nanos = TimeUnit.MILLISECONDS.toNanos(maxWaitMillis);

        for(;;) {
            AlienConnectionHolder holder;
            if (maxWait > 0) {
                holder = pollLast(nanos);
            } else {
                holder = takeLast();
            }
            holder.lastActiveTimeMillis = System.currentTimeMillis();
            lock.lock();
            try {
                if (activeCount < maxActive) {
                    activeCount++;
                    holder.active = true;
                }
            } finally {
                lock.unlock();
            }

            if (testOnBorrow) {
                boolean validate = testConnectionInternal(holder, holder.conn);
                if (!validate) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("skip not validate connection.");
                    }

                    discardConnection(holder);
                    continue;
                }
            } else {
                if (holder.conn.isClosed()) {
                    discardConnection(holder); // 传入null，避免重复关闭
                    continue;
                }

                if (testWhileIdle) {
                    long currentTimeMillis             = System.currentTimeMillis();
                    long lastActiveTimeMillis          = holder.lastActiveTimeMillis;
                    long lastExecTimeMillis            = holder.lastExecTimeMillis;
                    long lastKeepTimeMillis            = holder.lastKeepTimeMillis;

                    if (checkExecuteTime
                            && lastExecTimeMillis != lastActiveTimeMillis) {
                        lastActiveTimeMillis = lastExecTimeMillis;
                    }

                    if (lastKeepTimeMillis > lastActiveTimeMillis) {
                        lastActiveTimeMillis = lastKeepTimeMillis;
                    }

                    long idleMillis                    = currentTimeMillis - lastActiveTimeMillis;

                    long timeBetweenEvictionRunsMillis = this.timeBetweenEvictionRunsMillis;

                    if (timeBetweenEvictionRunsMillis <= 0) {
                        timeBetweenEvictionRunsMillis = DEFAULT_TIME_BETWEEN_EVICTION_RUNS_MILLIS;
                    }

                    if (idleMillis >= timeBetweenEvictionRunsMillis
                            || idleMillis < 0 // unexcepted branch
                    ) {
                        boolean validate = testConnectionInternal(holder, holder.conn);
                        if (!validate) {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("skip not validate connection.");
                            }

                            discardConnection(holder);
                            continue;
                        }
                    }
                }
            }

            return new AlienPooledConnection(holder);
        }

    }

    protected boolean testConnectionInternal(AlienConnectionHolder holder, Connection conn) {

        try {
            if (validConnectionChecker != null) {
                boolean valid = validConnectionChecker.isValidConnection(conn, validationQuery, -1);
                long currentTimeMillis = System.currentTimeMillis();
                if (holder != null) {
                    holder.lastValidTimeMillis = currentTimeMillis;
                    holder.lastExecTimeMillis = currentTimeMillis;
                }

                if (valid) { // unexcepted branch
                    long lastPacketReceivedTimeMs = MySqlUtils.getLastPacketReceivedTimeMs(conn);
                    if (lastPacketReceivedTimeMs > 0) {
                        long mysqlIdleMillis = currentTimeMillis - lastPacketReceivedTimeMs;
                        if (lastPacketReceivedTimeMs > 0 //
                                && mysqlIdleMillis >= timeBetweenEvictionRunsMillis) {
                            discardConnection(holder);
                            String errorMsg = "discard long time none received connection. "
                                    + ", jdbcUrl : " + url
                                    + ", lastPacketReceivedIdleMillis : " + mysqlIdleMillis;
                            LOG.warn(errorMsg);
                            return false;
                        }
                    }
                }
                return valid;
            }

            if (conn.isClosed()) {
                return false;
            }

            if (null == validationQuery) {
                return true;
            }

            Statement stmt = null;
            ResultSet rset = null;
            try {
                stmt = conn.createStatement();
                if (getValidationQueryTimeout() > 0) {
                    stmt.setQueryTimeout(validationQueryTimeout);
                }
                rset = stmt.executeQuery(validationQuery);
                if (!rset.next()) {
                    return false;
                }
            } finally {
                JdbcUtils.close(rset);
                JdbcUtils.close(stmt);
            }
            return true;
        } catch (Throwable ex) {
            // skip
            return false;
        }
    }

    public void discardConnection(AlienConnectionHolder holder) {
        discardConnection(holder.conn);
    }

    /**
     * 抛弃连接，不进行回收，而是抛弃
     *
     * @param realConnection
     * @deprecated
     */
    public void discardConnection(Connection realConnection) {
        JdbcUtils.close(realConnection);
        lock.lock();
        try {
            activeCount--;

            if (activeCount <= minIdle) {
                empty.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    private AlienConnectionHolder pollLast(long waitNanos) throws SQLException {
        try {
            lock.lockInterruptibly();
        } catch (InterruptedException e) {
            throw new SQLException("interrupt", e);
        }
        try {
            while (poolingCount == 0) {
                empty.signalAll();
                notEmpty.awaitNanos(waitNanos);
            }

            poolingCount--;
            AlienConnectionHolder last = connections[poolingCount];
            connections[poolingCount] = null;

            empty.signalAll();

            LOG.info("获取线程后pollLast，当前连接数：" + poolingCount);
            return last;
        } catch (InterruptedException e) {
            throw new SQLException(e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    private AlienConnectionHolder takeLast() throws SQLException {
        try {
            lock.lockInterruptibly();
        } catch (InterruptedException e) {
            throw new SQLException("interrupt", e);
        }
        try {
            while (poolingCount == 0) {
                empty.signalAll();
                notEmpty.await();
            }

            poolingCount--;
            AlienConnectionHolder last = connections[poolingCount];
            connections[poolingCount] = null;

            LOG.info("获取线程后takeLast，当前连接数：" + poolingCount);
            return last;
        } catch (InterruptedException e) {
            throw new SQLException(e.getMessage(), e);
        } finally {
            lock.unlock();
        }
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(final String url) {
        this.url = url;
    }

    public String getDriverClassName() {
        return driverClassName;
    }

    public void setDriverClassName(final String driverClassName) {
        this.driverClassName = driverClassName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(final String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(final String password) {
        this.password = password;
    }

    @Override
    public AlienPooledConnection getPooledConnection(final String user, final String password) throws SQLException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return logWriter;
    }

    @Override
    public void setLogWriter(final PrintWriter out) throws SQLException {
        this.logWriter = out;
    }

    @Override
    public void setLoginTimeout(final int seconds) throws SQLException {
        DriverManager.setLoginTimeout(seconds);
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return DriverManager.getLoginTimeout();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }

    public int getInitialSize() {
        return initialSize;
    }

    public void setInitialSize(final int initialSize) {
        this.initialSize = initialSize;
    }

    public boolean isKeepAlive() {
        return keepAlive;
    }

    public void setKeepAlive(final boolean keepAlive) {
        this.keepAlive = keepAlive;
    }

    public int getMaxActive() {
        return maxActive;
    }

    public void setMaxActive(final int maxActive) {
        this.maxActive = maxActive;
    }

    public int getMinIdle() {
        return minIdle;
    }

    public void setMinIdle(final int minIdle) {
        this.minIdle = minIdle;
    }

    public long getMaxWait() {
        return maxWait;
    }

    public void setMaxWait(final long maxWait) {
        this.maxWait = maxWait;
    }

    public boolean isTestOnBorrow() {
        return testOnBorrow;
    }

    public void setTestOnBorrow(final boolean testOnBorrow) {
        this.testOnBorrow = testOnBorrow;
    }

    public boolean isTestOnReturn() {
        return testOnReturn;
    }

    public void setTestOnReturn(final boolean testOnReturn) {
        this.testOnReturn = testOnReturn;
    }

    public boolean isTestWhileIdle() {
        return testWhileIdle;
    }

    public void setTestWhileIdle(final boolean testWhileIdle) {
        this.testWhileIdle = testWhileIdle;
    }

    public String getValidationQuery() {
        return validationQuery;
    }

    public void setValidationQuery(final String validationQuery) {
        this.validationQuery = validationQuery;
    }

    public int getValidationQueryTimeout() {
        return validationQueryTimeout;
    }

    public void setValidationQueryTimeout(final int validationQueryTimeout) {
        this.validationQueryTimeout = validationQueryTimeout;
    }

    public boolean isCheckExecuteTime() {
        return checkExecuteTime;
    }

    public void setCheckExecuteTime(final boolean checkExecuteTime) {
        this.checkExecuteTime = checkExecuteTime;
    }
}
