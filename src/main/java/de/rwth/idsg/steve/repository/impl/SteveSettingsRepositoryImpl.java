/*
 * SteVe - SteckdosenVerwaltung - https://github.com/RWTH-i5-IDSG/steve
 * Copyright (C) 2013-2022 RWTH Aachen University - Information Systems - Intelligent Distributed Systems Group (IDSG).
 * All Rights Reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package de.rwth.idsg.steve.repository.impl;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import de.rwth.idsg.steve.NotificationFeature;
import de.rwth.idsg.steve.SteveException;
import de.rwth.idsg.steve.repository.SteveSettingsRepository;
import de.rwth.idsg.steve.repository.dto.WebhookSettings;
import de.rwth.idsg.steve.repository.dto.MailSettings;
import de.rwth.idsg.steve.web.dto.SettingsForm;
import jooq.steve.db.tables.records.SteveSettingsRecord;
import org.jetbrains.annotations.Nullable;
import org.jooq.DSLContext;
import org.jooq.exception.DataAccessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static jooq.steve.db.tables.SteveSettings.STEVE_SETTINGS;

/**
 * @author Sevket Goekay <sevketgokay@gmail.com>
 * @since 06.11.2015
 */
@Repository
public class SteveSettingsRepositoryImpl implements SteveSettingsRepository {

    // Totally unnecessary to specify charset here. We just do it to make findbugs plugin happy.
    //
    private static final String APP_ID = new String(
            Base64.getEncoder().encode("SteckdosenVerwaltung".getBytes(StandardCharsets.UTF_8)),
            StandardCharsets.UTF_8
    );

    private static final Splitter SPLITTER = Splitter.on(",").trimResults().omitEmptyStrings();
    private static final Joiner JOINER = Joiner.on(",").skipNulls();

    @Autowired private DSLContext ctx;

    @Override
    public SettingsForm getForm() {
        SteveSettingsRecord r = getInternal();

        List<String> eMails = split(r.getMailRecipients());
        List<NotificationFeature> features = splitFeatures(r.getNotificationFeatures());

        return SettingsForm.builder()
                           .heartbeat(toMin(r.getHeartbeatIntervalInSeconds()))
                           .expiration(r.getHoursToExpire())
                           .enabled(r.getMailEnabled())
                           .webhookEnabled(r.getWebhookEnabled()) 
                           .webhook(r.getWebhook())
                           .host(r.getMailHost())
                           .username(r.getMailUsername())
                           .password(r.getMailPassword())
                           .from(r.getMailFrom())
                           .protocol(r.getMailProtocol())
                           .port(r.getMailPort())
                           .recipients(eMails)
                           .enabledFeatures(features)
                           .build();

    }

    @Override
    public MailSettings getMailSettings() {
        SteveSettingsRecord r = getInternal();

        List<String> eMails = split(r.getMailRecipients());
        List<NotificationFeature> features = splitFeatures(r.getNotificationFeatures());

        return MailSettings.builder()
                           .enabled(r.getMailEnabled())
                           .host(r.getMailHost())
                           .username(r.getMailUsername())
                           .password(r.getMailPassword())
                           .from(r.getMailFrom())
                           .protocol(r.getMailProtocol())
                           .port(r.getMailPort())
                           .recipients(eMails)
                           .enabledFeatures(features)
                           .build();
    }

    @Override
    public WebhookSettings getWebhookSettings() {
        SteveSettingsRecord r = getInternal();

        List<NotificationFeature> features = splitFeatures(r.getNotificationFeatures());

        return WebhookSettings.builder()
                            .webhookEnabled(r.getWebhookEnabled()) 
                            .webhook(r.getWebhook())
                            .enabledFeatures(features)
                            .build();
    }

    @Override
    public int getHeartbeatIntervalInSeconds() {
        return getInternal().getHeartbeatIntervalInSeconds();
    }

    @Override
    public int getHoursToExpire() {
        return getInternal().getHoursToExpire();
    }

    @Override
    public void update(SettingsForm form) {
        String eMails = join(form.getRecipients());
        String features = join(form.getEnabledFeatures());

        try {
            ctx.update(STEVE_SETTINGS)
               .set(STEVE_SETTINGS.HEARTBEAT_INTERVAL_IN_SECONDS, toSec(form.getHeartbeat()))
               .set(STEVE_SETTINGS.HOURS_TO_EXPIRE, form.getExpiration())
               .set(STEVE_SETTINGS.MAIL_ENABLED, form.getEnabled())
               .set(STEVE_SETTINGS.WEBHOOK_ENABLED, form.getWebhookEnabled())
               .set(STEVE_SETTINGS.WEBHOOK, form.getWebhook())
               .set(STEVE_SETTINGS.MAIL_HOST, form.getHost())
               .set(STEVE_SETTINGS.MAIL_USERNAME, form.getUsername())
               .set(STEVE_SETTINGS.MAIL_PASSWORD, form.getPassword())
               .set(STEVE_SETTINGS.MAIL_FROM, form.getFrom())
               .set(STEVE_SETTINGS.MAIL_PROTOCOL, form.getProtocol())
               .set(STEVE_SETTINGS.MAIL_PORT, form.getPort())
               .set(STEVE_SETTINGS.MAIL_RECIPIENTS, eMails)
               .set(STEVE_SETTINGS.NOTIFICATION_FEATURES, features)
               .where(STEVE_SETTINGS.APP_ID.eq(APP_ID))
               .execute();

        } catch (DataAccessException e) {
            throw new SteveException("FAILED to save the settings", e);
        }
    }

    private SteveSettingsRecord getInternal() {
        return ctx.selectFrom(STEVE_SETTINGS)
                  .where(STEVE_SETTINGS.APP_ID.eq(APP_ID))
                  .fetchOne();
    }

    private static int toMin(int seconds) {
        return (int) TimeUnit.SECONDS.toMinutes(seconds);
    }

    private static int toSec(int minutes) {
        return (int) TimeUnit.MINUTES.toSeconds(minutes);
    }

    @Nullable
    private String join(Collection<?> col) {
        if (col == null || col.isEmpty()) {
            return null;
        } else {
            // Use HashSet to trim duplicates before inserting into DB
            return JOINER.join(new HashSet<>(col));
        }
    }

    private List<String> split(String str) {
        if (str == null || str.isEmpty()) {
            return Collections.emptyList();
        } else {
            return SPLITTER.splitToList(str);
        }
    }

    private List<NotificationFeature> splitFeatures(String str) {
        return split(str).stream()
                         .map(NotificationFeature::fromName)
                         .collect(Collectors.toList());
    }
}
