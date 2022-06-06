package org.lsd.alien.demo.service;

import java.math.BigInteger;
import javax.annotation.Resource;
import org.lsd.alien.demo.dao.LocationDao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.lsd.alien.demo.entity.Location;

/**
 * @author nhsoft.lsd
 */
@Transactional
@Service
public class LocationServiceImpl implements LocationService {

    @Resource
    private LocationDao locationDao;

    @Override
    public Location read(final BigInteger merchantId, final BigInteger id) {
        return locationDao.read(merchantId, id);
    }
}
