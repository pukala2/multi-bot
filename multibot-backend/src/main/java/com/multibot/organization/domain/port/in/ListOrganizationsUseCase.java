package com.multibot.organization.domain.port.in;

import com.multibot.organization.domain.model.Organization;

import java.util.List;

public interface ListOrganizationsUseCase {

    List<Organization> listAll();
}
