package com.multibot.tenant.domain.port.in;

import com.multibot.tenant.domain.model.Tenant;

import java.util.List;

public interface ListTenantsUseCase {

    List<Tenant> listAll();
}
