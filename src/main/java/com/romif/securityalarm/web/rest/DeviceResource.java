package com.romif.securityalarm.web.rest;

import com.codahale.metrics.annotation.Timed;
import com.romif.securityalarm.config.Constants;
import com.romif.securityalarm.domain.Device;
import com.romif.securityalarm.security.AuthoritiesConstants;
import com.romif.securityalarm.service.DeviceService;
import com.romif.securityalarm.service.dto.DeviceDTO;
import com.romif.securityalarm.web.rest.util.HeaderUtil;
import io.swagger.annotations.ApiParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.inject.Inject;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

/**
 * Created by Roman_Konovalov on 1/26/2017.
 */
@RestController
@RequestMapping("/api")
public class DeviceResource {

    private final Logger log = LoggerFactory.getLogger(DeviceResource.class);

    @Inject
    private DeviceService deviceService;

    @GetMapping("/devices")
    @Timed
    @Secured(AuthoritiesConstants.USER)
    public ResponseEntity<List<DeviceDTO>> getAllDevices(@ApiParam Pageable pageable) throws URISyntaxException {
        String login =  ((org.springframework.security.core.userdetails.User) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getUsername();

        List<DeviceDTO> deviceDtos = deviceService.getAllDevices();

        return new ResponseEntity<>(deviceDtos, HttpStatus.OK);
    }

    @PostMapping("/devices")
    @Timed
    @Secured(AuthoritiesConstants.ADMIN)
    public ResponseEntity<?> createDevice(@RequestBody Device device) throws URISyntaxException {
        log.debug("REST request to save Device : {}", device);

        Device result = deviceService.createDevice(device);

        return ResponseEntity.created(new URI("/api/devices/" + result.getLogin()))
            .headers(HeaderUtil.createAlert( "A Device is created with identifier " + result.getLogin(), result.getLogin()))
            .body(result);
    }

    @DeleteMapping("/devices/{login:" + Constants.LOGIN_REGEX + "}")
    @Timed
    @Secured(AuthoritiesConstants.ADMIN)
    public ResponseEntity<Void> deleteDevice(@PathVariable String login) {
        log.debug("REST request to delete Device: {}", login);
        deviceService.deleteDevice(login);
        return ResponseEntity.ok().headers(HeaderUtil.createAlert( "A Device is deleted with identifier " + login, login)).build();
    }

    @PostMapping("/devices/{login:" + Constants.LOGIN_REGEX + "}/login")
    @Timed
    @Secured(AuthoritiesConstants.ADMIN)
    public ResponseEntity<Void> loginDevice(@PathVariable String login) {
        log.debug("REST request to delete Device: {}", login);
        deviceService.deleteDevice(login);
        return ResponseEntity.ok().headers(HeaderUtil.createAlert( "A user is deleted with identifier " + login, login)).build();
    }
}
