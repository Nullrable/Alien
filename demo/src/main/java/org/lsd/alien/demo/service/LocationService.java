package org.lsd.alien.demo.service;

import java.math.BigInteger;
import org.lsd.alien.demo.entity.Location;

/**
 * @author nhsoft.lsd
 */
public interface LocationService {

    Location read(BigInteger merchantId, BigInteger id);
}
