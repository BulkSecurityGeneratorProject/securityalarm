package com.romif.securityalarm.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.romif.securityalarm.api.dto.DeviceAction;
import com.romif.securityalarm.config.ApplicationProperties;
import com.romif.securityalarm.domain.ConfigStatus;
import com.romif.securityalarm.domain.Device;
import com.romif.securityalarm.domain.DeviceCredentials;
import com.romif.securityalarm.repository.DeviceCredentialsRepository;
import com.romif.securityalarm.repository.DeviceRepository;
import com.romif.securityalarm.service.dto.DeviceDTO;
import com.romif.securityalarm.service.dto.DeviceManagementDTO;
import com.romif.securityalarm.service.mapper.DeviceMapper;
import com.romif.securityalarm.service.util.RandomUtil;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.security.oauth2.provider.token.store.JdbcTokenStore;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class DeviceService {

    public static final int DEVICE_ACTION_TIMEOUT = 120;

    private static final ScheduledExecutorService schedulerExecutor = Executors.newScheduledThreadPool(20);
    private final Logger log = LoggerFactory.getLogger(DeviceService.class);
    private final Cache<String, Pair<DeviceAction, CompletableFuture<Boolean>>> deviceActions = CacheBuilder.newBuilder()
        .expireAfterWrite(DEVICE_ACTION_TIMEOUT, TimeUnit.SECONDS)
        .build();
    @Inject
    private JdbcTokenStore tokenStore;
    @Inject
    private ApplicationProperties applicationProperties;
    @Inject
    private DeviceCredentialsRepository deviceCredentialsRepository;
    @Inject
    private PasswordEncoder passwordEncoder;
    @Inject
    private DeviceRepository deviceRepository;
    @Inject
    private DeviceMapper deviceMapper;
    @Inject
    private SmsTxtlocalService smsService;

    public List<DeviceManagementDTO> getAllDevices(String login) {

        Collection<OAuth2AccessToken> tokens = tokenStore.findTokensByClientId(applicationProperties.getSecurity().getAuthentication().getOauth().getClientid());

        return deviceCredentialsRepository.findAllByDeviceUserLogin(login).stream()
            .map(deviceCredentials -> {
                DeviceManagementDTO deviceDTO = deviceMapper.deviceToDeviceManagementDTO(deviceCredentials.getDevice());
                deviceDTO.setAuthorized(tokens.stream().anyMatch(oAuth2AccessToken -> oAuth2AccessToken.getValue().equals(deviceCredentials.getToken())));
                deviceDTO.setToken(deviceCredentials.getToken());
                deviceDTO.setPauseToken(deviceCredentials.getPauseToken());
                deviceDTO.setSecret(deviceCredentials.getSecret());
                return deviceDTO;
            })
            .collect(Collectors.toList());
    }

    public List<DeviceDTO> getAllLoggedDevices(String login) {

        return getAllDevices(login).stream()
            .filter(DeviceManagementDTO::isAuthorized)
            .map(deviceManagementDTO -> deviceMapper.deviceManagementDTOToDeviceDTO(deviceManagementDTO))
            .collect(Collectors.toList());
    }

    public Device createDevice(Device device) {

        String rawPassword = RandomUtil.generatePassword();
        String pauseToken = UUID.randomUUID().toString();
        String secret = RandomStringUtils.randomAlphanumeric(8);

        device.setPassword(passwordEncoder.encode(rawPassword));

        Device result = deviceRepository.save(device);

        DeviceCredentials deviceCredentials = new DeviceCredentials(null, result, rawPassword, UUID.randomUUID().toString(), pauseToken, secret);

        deviceCredentialsRepository.save(deviceCredentials);

        return result;
    }

    public boolean updateDevice(DeviceDTO deviceDTO) {
        return deviceRepository.findOneById(deviceDTO.getId()).map(device -> {
            device.setPhone(deviceDTO.getPhone());
            device.setApn(deviceDTO.getApn());
            device.setDescription(deviceDTO.getDescription());
            deviceRepository.save(device);
            return true;
        }).orElse(false);
    }

    public void deleteDevice(String login) {
        tokenStore.findTokensByUserName(login).forEach(token ->
            tokenStore.removeAccessToken(token));
        deviceRepository.findOneByLogin(login).ifPresent(device -> {
            deviceRepository.delete(device);
            log.debug("Deleted Device: {}", device);
        });
    }

    public CompletableFuture<ConfigStatus> configDevice(String login) {
        Optional<Device> device = deviceRepository.findOneByLogin(login);
        Optional<DeviceCredentials> deviceCredentials = deviceCredentialsRepository.findOneByDeviceLogin(login);
        if (device.isPresent() && deviceCredentials.isPresent()) {
            CompletableFuture<ConfigStatus> future = smsService.sendConfig(device.get(), deviceCredentials.get());
            future.thenApply(configStatus -> {
                device.get().setConfigStatus(configStatus);
                deviceRepository.save(device.get());
                return configStatus;
            });
            return future;
        } else {
            return CompletableFuture.completedFuture(ConfigStatus.NOT_CONFIGURED);
        }
    }

    public boolean logoutDevice(String login) {
        tokenStore.findTokensByUserName(login).forEach(token ->
            tokenStore.removeAccessToken(token));

        return tokenStore.findTokensByUserName(login).isEmpty();

    }

    public CompletableFuture<Boolean> reboot(String login) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        deviceActions.put(login, Pair.of(DeviceAction.REBOOT, result));
        schedulerExecutor.schedule(() -> result.complete(false), DEVICE_ACTION_TIMEOUT, TimeUnit.SECONDS);
        return result;
    }

    public CompletableFuture<Boolean> halt(String login) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        deviceActions.put(login, Pair.of(DeviceAction.HALT, result));
        schedulerExecutor.schedule(() -> result.complete(false), DEVICE_ACTION_TIMEOUT, TimeUnit.SECONDS);
        return result;
    }

    public Pair<DeviceAction, CompletableFuture<Boolean>> completeDeviceAction(String login) {
        Pair<DeviceAction, CompletableFuture<Boolean>> result = deviceActions.getIfPresent(login);
        if (result != null) {
            deviceActions.invalidate(login);
        }
        return result;
    }

}
