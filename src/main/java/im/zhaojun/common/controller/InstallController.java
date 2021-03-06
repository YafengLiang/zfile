package im.zhaojun.common.controller;

import cn.hutool.crypto.SecureUtil;
import im.zhaojun.common.model.StorageConfig;
import im.zhaojun.common.model.dto.InstallModelDTO;
import im.zhaojun.common.model.dto.ResultBean;
import im.zhaojun.common.model.dto.SystemConfigDTO;
import im.zhaojun.common.model.enums.StorageTypeEnum;
import im.zhaojun.common.service.AbstractFileService;
import im.zhaojun.common.service.FileAsyncCacheService;
import im.zhaojun.common.service.StorageConfigService;
import im.zhaojun.common.service.SystemConfigService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 系统安装初始化
 * @author zhaojun
 */
@Controller
public class InstallController {

    @Resource
    private SystemConfigService systemConfigService;

    @Resource
    private StorageConfigService storageConfigService;

    @Resource
    private AdminController adminController;

    @Resource
    private FileAsyncCacheService fileAsyncCacheService;

    @GetMapping("/is-installed")
    @ResponseBody
    public ResultBean isInstall() {
        if (systemConfigService.getCurrentStorageStrategy() == null) {
            return ResultBean.success();
        }
        return ResultBean.error("请勿重复初始化");
    }


    @PostMapping("/install")
    @ResponseBody
    public ResultBean install(InstallModelDTO installModelDTO) throws Exception {
        SystemConfigDTO systemConfigDTO = systemConfigService.getSystemConfig();

        if (systemConfigDTO.getStorageStrategy() != null) {
            return ResultBean.error("请勿重复初始化.");
        }

        systemConfigDTO.setSiteName(installModelDTO.getSiteName());
        StorageTypeEnum storageTypeEnum = installModelDTO.getStorageStrategy();
        systemConfigDTO.setStorageStrategy(storageTypeEnum);
        systemConfigDTO.setUsername(installModelDTO.getUsername());
        systemConfigDTO.setPassword(SecureUtil.md5(installModelDTO.getPassword()));
        systemConfigDTO.setDomain(installModelDTO.getDomain());
        systemConfigService.updateSystemConfig(systemConfigDTO);

        Map<String, String> storageStrategyConfig = installModelDTO.getStorageStrategyConfig();

        List<StorageConfig> storageConfigList = storageConfigService.selectStorageConfigByType(storageTypeEnum);
        for (StorageConfig storageConfig : storageConfigList) {
            String key = storageConfig.getKey();
            String value = storageStrategyConfig.get(key);
            storageConfig.setValue(value);
        }

        storageConfigService.updateStorageConfig(storageConfigList);
        adminController.refreshStorageStrategy();
        return ResultBean.success();
    }

    @PostMapping("/storage-strategy")
    @ResponseBody
    public ResultBean save(@RequestParam Map<String, String> storageStrategyConfig, StorageTypeEnum storageStrategy) throws Exception {
        List<StorageConfig> storageConfigList = storageConfigService.selectStorageConfigByType(storageStrategy);
        for (StorageConfig storageConfig : storageConfigList) {
            String key = storageConfig.getKey();
            String value = storageStrategyConfig.get(key);
            storageConfig.setValue(value);
        }
        storageConfigService.updateStorageConfig(storageConfigList);

        StorageTypeEnum currentStorageStrategy = systemConfigService.getCurrentStorageStrategy();
        if (Objects.equals(storageStrategy, currentStorageStrategy)) {
            AbstractFileService fileService = systemConfigService.getCurrentFileService();
            fileService.clearCache();

            if (systemConfigService.getEnableCache()) {
                fileAsyncCacheService.cacheGlobalFile();
            }
            fileService.init();
        }

        return ResultBean.success();
    }
}
