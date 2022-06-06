package org.lsd.alien.demo.dao;

import java.math.BigInteger;
import org.lsd.alien.demo.entity.Location;
import org.springframework.stereotype.Repository;

/**
 * (Location)表数据库访问层.
 *
 * @author nhsoft.hecs
 */
@Repository
public interface LocationDao {

    /**
     * 通过ID查询单条数据.
     *
     * @param id 主键
     * @param merchantId 商家id
     * @return 实例对象
     */
    Location read(BigInteger merchantId, BigInteger id);

}
