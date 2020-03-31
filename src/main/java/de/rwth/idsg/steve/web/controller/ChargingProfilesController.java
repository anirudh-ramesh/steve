/*
 * SteVe - SteckdosenVerwaltung - https://github.com/RWTH-i5-IDSG/steve
 * Copyright (C) 2013-2020 RWTH Aachen University - Information Systems - Intelligent Distributed Systems Group (IDSG).
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
package de.rwth.idsg.steve.web.controller;

import de.rwth.idsg.steve.repository.ChargePointRepository;
import de.rwth.idsg.steve.repository.ChargingProfileRepository;
import de.rwth.idsg.steve.repository.dto.ChargingProfile;
import de.rwth.idsg.steve.utils.DateTimeUtils;
import de.rwth.idsg.steve.web.dto.ChargingProfileAssignmentQueryForm;
import de.rwth.idsg.steve.web.dto.ChargingProfileForm;
import de.rwth.idsg.steve.web.dto.ChargingProfileQueryForm;
import jooq.steve.db.tables.records.ChargingProfileRecord;
import jooq.steve.db.tables.records.ChargingSchedulePeriodRecord;
import ocpp.cp._2015._10.ChargingProfileKindType;
import ocpp.cp._2015._10.ChargingProfilePurposeType;
import ocpp.cp._2015._10.ChargingRateUnitType;
import ocpp.cp._2015._10.RecurrencyKindType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @author Sevket Goekay <goekay@dbis.rwth-aachen.de>
 * @since 12.11.2018
 */
@Controller
@RequestMapping(value = "/manager/chargingProfiles")
public class ChargingProfilesController {

    @Autowired private ChargePointRepository chargePointRepository;
    @Autowired private ChargingProfileRepository repository;

    private static final String PARAMS = "params";

    // -------------------------------------------------------------------------
    // Paths
    // -------------------------------------------------------------------------

    private static final String QUERY_PATH = "/query";

    private static final String DETAILS_PATH = "/details/{chargingProfilePk}";
    private static final String DELETE_PATH = "/delete/{chargingProfilePk}";
    private static final String UPDATE_PATH = "/update";
    private static final String ADD_PATH = "/add";

    private static final String ASSIGNMENTS_PATH = "/assignments";

    // -------------------------------------------------------------------------
    // HTTP methods
    // -------------------------------------------------------------------------

    @RequestMapping(method = RequestMethod.GET)
    public String getOverview(Model model) {
        ChargingProfileQueryForm queryForm = new ChargingProfileQueryForm();
        model.addAttribute(PARAMS, queryForm);
        initList(queryForm, model);
        return "data-man/chargingProfiles";
    }

    @RequestMapping(value = QUERY_PATH, method = RequestMethod.GET)
    public String getQuery(@ModelAttribute(PARAMS) ChargingProfileQueryForm queryForm, Model model) {
        initList(queryForm, model);
        return "data-man/chargingProfiles";
    }

    @RequestMapping(value = ADD_PATH, method = RequestMethod.GET)
    public String addGet(Model model) {
        model.addAttribute("form", new ChargingProfileForm());
        return "data-man/chargingProfileAdd";
    }

    @RequestMapping(params = "add", value = ADD_PATH, method = RequestMethod.POST)
    public String addPost(@Valid @ModelAttribute("form") ChargingProfileForm form,
                          BindingResult result, Model model) {
        if (result.hasErrors()) {
            return "data-man/chargingProfileAdd";
        }

        repository.add(form);
        return toOverview();
    }

    @RequestMapping(params = "update", value = UPDATE_PATH, method = RequestMethod.POST)
    public String update(@Valid @ModelAttribute("form") ChargingProfileForm form,
                         BindingResult result, Model model) {
        if (result.hasErrors()) {
            return "data-man/chargingProfileDetails";
        }

        repository.update(form);
        return toOverview();
    }

    @RequestMapping(params = "backToOverview", value = ADD_PATH, method = RequestMethod.POST)
    public String addBackToOverview() {
        return toOverview();
    }

    @RequestMapping(params = "backToOverview", value = UPDATE_PATH, method = RequestMethod.POST)
    public String updateBackToOverview() {
        return toOverview();
    }

    @RequestMapping(value = DELETE_PATH, method = RequestMethod.POST)
    public String delete(@PathVariable("chargingProfilePk") int chargingProfilePk) {
        repository.delete(chargingProfilePk);
        return toOverview();
    }

    @RequestMapping(value = DETAILS_PATH, method = RequestMethod.GET)
    public String getDetails(@PathVariable("chargingProfilePk") int chargingProfilePk, Model model) {
        ChargingProfile.Details details = repository.getDetails(chargingProfilePk);

        ChargingProfileRecord profile = details.getProfile();
        List<ChargingSchedulePeriodRecord> periods = details.getPeriods();

        ChargingProfileForm form = new ChargingProfileForm();
        form.setChargingProfilePk(profile.getChargingProfilePk());
        form.setDescription(profile.getDescription());
        form.setNote(profile.getNote());
        form.setStackLevel(profile.getStackLevel());
        form.setChargingProfilePurpose(ChargingProfilePurposeType.fromValue(profile.getChargingProfilePurpose()));
        form.setChargingProfileKind(ChargingProfileKindType.fromValue(profile.getChargingProfileKind()));
        form.setRecurrencyKind(profile.getRecurrencyKind() == null ? null : RecurrencyKindType.fromValue(profile.getRecurrencyKind()));
        form.setValidFrom(DateTimeUtils.toLocalDateTime(profile.getValidFrom()));
        form.setValidTo(DateTimeUtils.toLocalDateTime(profile.getValidTo()));
        form.setDurationInSeconds(profile.getDurationInSeconds());
        form.setStartSchedule(DateTimeUtils.toLocalDateTime(profile.getStartSchedule()));
        form.setChargingRateUnit(ChargingRateUnitType.fromValue(profile.getChargingRateUnit()));
        form.setMinChargingRate(profile.getMinChargingRate());

        Map<String, ChargingProfileForm.SchedulePeriod> periodMap = new LinkedHashMap<>();
        for (ChargingSchedulePeriodRecord rec : periods) {
            ChargingProfileForm.SchedulePeriod p = new ChargingProfileForm.SchedulePeriod();
            p.setStartPeriodInSeconds(rec.getStartPeriodInSeconds());
            p.setPowerLimitInAmperes(rec.getPowerLimitInAmperes());
            p.setNumberPhases(rec.getNumberPhases());

            periodMap.put(UUID.randomUUID().toString(), p);
        }
        form.setSchedulePeriodMap(periodMap);

        model.addAttribute("form", form);
        return "data-man/chargingProfileDetails";
    }

    @RequestMapping(value = ASSIGNMENTS_PATH, method = RequestMethod.GET)
    public String getAssignments(@ModelAttribute(PARAMS) ChargingProfileAssignmentQueryForm form, Model model) {
        model.addAttribute(PARAMS, form);
        model.addAttribute("profileList", repository.getBasicInfo());
        model.addAttribute("cpList", chargePointRepository.getChargeBoxIds());
        model.addAttribute("assignments", repository.getAssignments(form));
        return "data-man/chargingProfileAssignments";
    }

    private void initList(ChargingProfileQueryForm queryForm, Model model) {
        model.addAttribute("profileList", repository.getOverview(queryForm));
    }

    private String toOverview() {
        return "redirect:/manager/chargingProfiles";
    }
}
