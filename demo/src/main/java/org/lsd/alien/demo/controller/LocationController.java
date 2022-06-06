package org.lsd.alien.demo.controller;

import java.math.BigInteger;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.lsd.alien.demo.entity.Location;
import org.lsd.alien.demo.service.LocationService;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author nhsoft.lsd
 */
@RestController
@RequestMapping("/admin/api/internal/")
@Validated
@Slf4j
public class LocationController {

    @Resource
    private LocationService locationService;

    @GetMapping(value = "locations/{location_id}.json")
    @ResponseStatus(value = HttpStatus.OK)
    public Location read(
            @RequestHeader(value = "x-mars-merchant-id") final BigInteger merchantId,
            @PathVariable(value = "location_id") final BigInteger id) {

        Location locationDTO = locationService.read(merchantId, id);

        return locationDTO;
    }
}
