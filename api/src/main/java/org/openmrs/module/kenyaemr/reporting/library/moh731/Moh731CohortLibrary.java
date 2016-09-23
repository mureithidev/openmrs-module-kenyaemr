/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */

package org.openmrs.module.kenyaemr.reporting.library.moh731;

import org.openmrs.EncounterType;
import org.openmrs.module.kenyacore.report.ReportUtils;
import org.openmrs.module.kenyacore.report.cohort.definition.CalculationCohortDefinition;
import org.openmrs.module.kenyaemr.calculation.library.MissedLastAppointmentCalculation;
import org.openmrs.module.kenyaemr.calculation.library.hiv.CtxFromAListOfMedicationOrdersCalculation;
import org.openmrs.module.kenyaemr.calculation.library.hiv.NextOfVisitHigherThanContextCalculation;
import org.openmrs.module.kenyaemr.metadata.HivMetadata;
import org.openmrs.module.kenyaemr.reporting.library.shared.common.CommonCohortLibrary;
import org.openmrs.module.kenyaemr.reporting.library.shared.hiv.HivCohortLibrary;
import org.openmrs.module.kenyaemr.reporting.library.shared.hiv.art.ArtCohortLibrary;
import org.openmrs.module.metadatadeploy.MetadataUtils;
import org.openmrs.module.reporting.cohort.definition.CohortDefinition;
import org.openmrs.module.reporting.cohort.definition.CompositionCohortDefinition;
import org.openmrs.module.reporting.evaluation.parameter.Parameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * Library of cohort definitions used specifically in the MOH731 report
 */
@Component
public class Moh731CohortLibrary {

	@Autowired
	private CommonCohortLibrary commonCohorts;

	@Autowired
	private ArtCohortLibrary artCohorts;

	@Autowired
	private HivCohortLibrary hivCohortLibrary;

	/**
	 * Patients currently in care (includes transfers)
	 * @return the cohort definition
	 */
	public CohortDefinition currentlyInCare() {
		EncounterType hivEnroll = MetadataUtils.existing(EncounterType.class, HivMetadata._EncounterType.HIV_ENROLLMENT);
		EncounterType hivConsult = MetadataUtils.existing(EncounterType.class, HivMetadata._EncounterType.HIV_CONSULTATION);

		//find those patients who have next of appointment date
		CalculationCohortDefinition nextAppointment = new CalculationCohortDefinition(new NextOfVisitHigherThanContextCalculation());
		nextAppointment.setName("Have date of next visit");
		nextAppointment.addParameter(new Parameter("onDate", "On Date", Date.class));

		CalculationCohortDefinition ctxFromAListOfMedicationOrders = new CalculationCohortDefinition(new CtxFromAListOfMedicationOrdersCalculation());
		ctxFromAListOfMedicationOrders.setName("ctxFromAListOfMedicationOrders");
		ctxFromAListOfMedicationOrders.addParameter(new Parameter("OnDate", "On Date", Date.class));

		CompositionCohortDefinition cd = new CompositionCohortDefinition();
		cd.addParameter(new Parameter("onDate", "On Date", Date.class));

		cd.addSearch("recentEncounter", ReportUtils.map(commonCohorts.hasEncounter(hivEnroll, hivConsult), "onOrAfter=${onDate-90d},onOrBefore=${onDate}"));
		cd.addSearch("appointmentHigher", ReportUtils.map(nextAppointment, "onDate=${onDate}"));
		cd.addSearch("hasCtx", ReportUtils.map(ctxFromAListOfMedicationOrders, "onDate=${onDate}"));
		cd.addSearch("inHivProgram", ReportUtils.map(hivCohortLibrary.enrolled(), "enrolledOnOrBefore=${onDate}"));
		cd.addSearch("ltfDeadTo", ReportUtils.map(hivCohortLibrary.transferredOutDeadAndLtfu(), "onOrBefore=${onDate}"));
		cd.setCompositionString("((recentEncounter OR appointmentHigher OR hasCtx) AND inHivProgram) AND NOT ltfDeadTo");
		return cd;
	}

	/**
	 *
	 */
	public CohortDefinition missedAppointment(){
		CalculationCohortDefinition cdMissed = new CalculationCohortDefinition(new MissedLastAppointmentCalculation());
		cdMissed.setName("Missed appointment patients");
		cdMissed.addParameter(new Parameter("OnDate", "On Date", Date.class));
		return cdMissed;
	}

	/**
	 * Patients who ART revisits
	 * @return the cohort definition
	 */
	public CohortDefinition revisitsArt() {
		CompositionCohortDefinition cd = new CompositionCohortDefinition();
		cd.addParameter(new Parameter("fromDate", "From Date", Date.class));
		cd.addParameter(new Parameter("toDate", "To Date", Date.class));
		cd.addSearch("inCare", ReportUtils.map(currentlyInCare(), "onDate=${toDate}"));
		cd.addSearch("startedBefore", ReportUtils.map(artCohorts.startedArt(), "onOrBefore=${fromDate-1d}"));
		cd.addSearch("missedAppointment", ReportUtils.map(missedAppointment(), "onDate=${onDate}"));
		cd.addSearch("toDeadLtf", ReportUtils.map(hivCohortLibrary.transferredOutDeadLtfuAndMissedAppointment(), "onOrBefore=${toDate}"));
		cd.setCompositionString("inCare AND startedBefore AND NOT toDeadLtf");
		return cd;
	}

	/**
	 * Has appointment date and started art
	 */
	public CohortDefinition hasAppointmentDateAndStartedArt() {
		CalculationCohortDefinition hasAppointmentDate = new CalculationCohortDefinition( new NextOfVisitHigherThanContextCalculation());
		hasAppointmentDate.setName("Having appointment higher than context");
		hasAppointmentDate.addParameter(new Parameter("onDate", "On Date", Date.class));

		CompositionCohortDefinition cd = new CompositionCohortDefinition();
		cd.setName("Appointment and started art");
		cd.addParameter(new Parameter("onOrBefore", "Before Date", Date.class));
		cd.addSearch("startedArt", ReportUtils.map(artCohorts.startedArt(), "onOrBefore=${onOrBefore}"));
		cd.addSearch("hasAppointmentDate", ReportUtils.map(hasAppointmentDate, "onDate=${onOrBefore}"));
		cd.setCompositionString("startedArt AND hasAppointmentDate");
		return cd;
	}

	/**
	 * Currently on ART.. we could calculate this several ways...
	 * @return the cohort definition
 	 */
	public CohortDefinition currentlyOnArt() {
		CompositionCohortDefinition cd = new CompositionCohortDefinition();
		cd.addParameter(new Parameter("fromDate", "From Date", Date.class));
		cd.addParameter(new Parameter("toDate", "To Date", Date.class));
		cd.addSearch("startedArt", ReportUtils.map(artCohorts.startedArt(), "onOrAfter=${fromDate},onOrBefore=${toDate}"));
		cd.addSearch("revisitsArt", ReportUtils.map(revisitsArt(), "fromDate=${fromDate},toDate=${toDate}"));
		cd.addSearch("hasAppointmentDateAndStartedART", ReportUtils.map(hasAppointmentDateAndStartedArt(), "onOrBefore=${toDate}"));
		cd.addSearch("toDeadLtf", ReportUtils.map(hivCohortLibrary.transferredOutDeadLtfuAndMissedAppointment(), "onOrBefore=${toDate}"));
		cd.setCompositionString("(startedArt OR revisitsArt OR hasAppointmentDateAndStartedART) AND NOT toDeadLtf");
		return cd;
	}

	/**
	 * Taking original 1st line ART at 12 months
	 * @return the cohort definition
	 */
	public CohortDefinition onOriginalFirstLineAt12Months() {
		CompositionCohortDefinition cd = new CompositionCohortDefinition();
		cd.addParameter(new Parameter("fromDate", "From Date", Date.class));
		cd.addParameter(new Parameter("toDate", "To Date", Date.class));
		cd.addSearch("art12MonthNetCohort", ReportUtils.map(artCohorts.netCohortMonths(12), "onDate=${toDate}"));
		cd.addSearch("currentlyOnOriginalFirstLine", ReportUtils.map(artCohorts.onOriginalFirstLine(), "onDate=${toDate}"));
		cd.setCompositionString("art12MonthNetCohort AND currentlyOnOriginalFirstLine");
		return cd;
	}

	/**
	 * Taking alternate 1st line ART at 12 months
	 * @return the cohort definition
	 */
	public CohortDefinition onAlternateFirstLineAt12Months() {
		CompositionCohortDefinition cd = new CompositionCohortDefinition();
		cd.addParameter(new Parameter("fromDate", "From Date", Date.class));
		cd.addParameter(new Parameter("toDate", "To Date", Date.class));
		cd.addSearch("art12MonthNetCohort", ReportUtils.map(artCohorts.netCohortMonths(12), "onDate=${toDate}"));
		cd.addSearch("currentlyOnAlternateFirstLine", ReportUtils.map(artCohorts.onAlternateFirstLine(), "onDate=${toDate}"));
		cd.setCompositionString("art12MonthNetCohort AND currentlyOnAlternateFirstLine");
		return cd;
	}

	/**
	 * Taking 2nd line ART at 12 months
	 * @return the cohort definition
	 */
	public CohortDefinition onSecondLineAt12Months() {
		CompositionCohortDefinition cd = new CompositionCohortDefinition();
		cd.addParameter(new Parameter("fromDate", "From Date", Date.class));
		cd.addParameter(new Parameter("toDate", "To Date", Date.class));
		cd.addSearch("art12MonthNetCohort", ReportUtils.map(artCohorts.netCohortMonths(12), "onDate=${toDate}"));
		cd.addSearch("currentlyOnSecondLine", ReportUtils.map(artCohorts.onSecondLine(), "onDate=${toDate}"));
		cd.setCompositionString("art12MonthNetCohort AND currentlyOnSecondLine");
		return cd;
	}

	/**
	 * Taking any ART at 12 months
	 * @return the cohort definition
	 */
	public CohortDefinition onTherapyAt12Months() {
		CompositionCohortDefinition cd = new CompositionCohortDefinition();
		cd.addParameter(new Parameter("fromDate", "From Date", Date.class));
		cd.addParameter(new Parameter("toDate", "To Date", Date.class));
		cd.addSearch("art12MonthNetCohort", ReportUtils.map(artCohorts.netCohortMonths(12), "onDate=${toDate}"));
		cd.addSearch("currentlyOnArt", ReportUtils.map(artCohorts.onArt(), "onDate=${toDate}"));
		cd.setCompositionString("art12MonthNetCohort AND currentlyOnArt");
		return cd;
	}
}