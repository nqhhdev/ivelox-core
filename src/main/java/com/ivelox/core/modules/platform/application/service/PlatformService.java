package com.ivelox.core.modules.platform.application.service;

import org.springframework.stereotype.Service;

import com.ivelox.core.config.IveloxProperties;
import com.ivelox.core.modules.platform.application.port.in.GetFeatureFlagsUseCase;
import com.ivelox.core.modules.platform.domain.model.FeatureFlags;

@Service
public class PlatformService implements GetFeatureFlagsUseCase {

    private final IveloxProperties props;

    public PlatformService(IveloxProperties props) {
        this.props = props;
    }

    @Override
    public FeatureFlags getFeatureFlags() {
        return new FeatureFlags(props.healthEnabled(), props.financeEnabled(), props.paymentApprovalEnabled());
    }
}
