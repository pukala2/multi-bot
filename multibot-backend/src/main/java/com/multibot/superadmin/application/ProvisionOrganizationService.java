package com.multibot.superadmin.application;

import com.multibot.auth.domain.port.in.CreateUserUseCase;
import com.multibot.bots.domain.port.in.AddBotUseCase;
import com.multibot.bots.domain.port.in.AddBotUseCase.AddBotResult;
import com.multibot.organization.domain.model.Organization;
import com.multibot.organization.domain.port.in.CreateOrganizationUseCase;
import com.multibot.superadmin.domain.port.in.ProvisionOrganizationUseCase;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Provisioning klienta przez super admina: organizacja + owner (auto-haslo) + pierwszy bot.
 * Samo tworzenie bota (tenant + wersja + widget) deleguje do modulu bots ({@link AddBotUseCase}).
 */
@Service
class ProvisionOrganizationService implements ProvisionOrganizationUseCase {

    private static final SecureRandom RND = new SecureRandom();

    private final CreateOrganizationUseCase createOrganization;
    private final CreateUserUseCase createUser;
    private final AddBotUseCase addBot;

    ProvisionOrganizationService(CreateOrganizationUseCase createOrganization,
                                 CreateUserUseCase createUser,
                                 AddBotUseCase addBot) {
        this.createOrganization = createOrganization;
        this.createUser = createUser;
        this.addBot = addBot;
    }

    @Override
    public ProvisionResult provision(ProvisionCommand cmd) {
        // 1. organizacja (klient)
        Organization org = createOrganization.create(cmd.name(), cmd.plan(), cmd.companyName(), cmd.nip());

        // 2. owner z wygenerowanym haslem
        String rawPassword = generatePassword();
        createUser.createOwner(org.id(), cmd.ownerEmail(), rawPassword);

        // 3. pierwszy bot (super admin tworzy bez sprawdzania limitu planu)
        String botName = (cmd.firstBotName() == null || cmd.firstBotName().isBlank())
                ? cmd.name()
                : cmd.firstBotName();
        AddBotResult bot = addBot.addBot(org.id(), botName, cmd.firstBotUrl());

        return new ProvisionResult(org.id(), org.name(), cmd.ownerEmail(), rawPassword,
                bot.botId(), bot.botName(), bot.widgetKey());
    }

    /** Mocne haslo: ~12 znakow base64url + sufiks zapewniajacy klasy znakow. */
    private static String generatePassword() {
        byte[] b = new byte[9];
        RND.nextBytes(b);
        String core = Base64.getUrlEncoder().withoutPadding().encodeToString(b)
                .replaceAll("[-_]", "");
        return core + "Aa1!";
    }
}
