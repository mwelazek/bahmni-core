package org.bahmni.module.bahmnicore.web.v1_0.controller;

import org.bahmni.module.bahmnicore.web.v1_0.VisitClosedException;

import org.openmrs.Encounter;
import org.openmrs.Visit;

import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.Provider;
import org.openmrs.api.LocationService;
import org.openmrs.api.PatientService;
import org.openmrs.api.ProviderService;

import org.openmrs.api.EncounterService;
import org.openmrs.module.bahmniemrapi.encountertransaction.contract.BahmniEncounterSearchParameters;
import org.openmrs.module.bahmniemrapi.encountertransaction.contract.BahmniEncounterTransaction;
import org.openmrs.module.bahmniemrapi.encountertransaction.contract.BahmniObservation;

import org.openmrs.module.bahmniemrapi.encountertransaction.mapper.BahmniEncounterTransactionMapper;
import org.openmrs.module.bahmniemrapi.encountertransaction.service.BahmniEncounterTransactionService;
import org.openmrs.module.emrapi.encounter.EmrEncounterService;
import org.openmrs.module.emrapi.encounter.EncounterTransactionMapper;
import org.openmrs.module.emrapi.encounter.domain.EncounterTransaction;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.v1_0.controller.BaseRestController;

//import org.openmrs.module.appointments.model.Appointment;
//import org.openmrs.module.appointments.model.AppointmentService;
//import org.openmrs.module.appointments.model.AppointmentServiceType;
//import org.openmrs.module.appointments.model.AppointmentStatus;
//import org.openmrs.module.appointments.model.Appointment;

import org.openmrs.module.appointments.model.*;
import org.openmrs.module.appointments.service.AppointmentServiceService;
import org.openmrs.module.appointments.service.AppointmentsService;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.Set;

import static org.bahmni.module.bahmnicore.util.MiscUtils.setUuidsForObservations;
import static org.bahmni.module.bahmnicore.util.MiscUtils.getFollowUpDateObservation;

@Controller
@RequestMapping(value = "/rest/" + RestConstants.VERSION_1 + "/bahmnicore/bahmniencounter")
public class BahmniEncounterController extends BaseRestController {
    private EncounterService encounterService;
    private EmrEncounterService emrEncounterService;
    private EncounterTransactionMapper encounterTransactionMapper;
    private BahmniEncounterTransactionService bahmniEncounterTransactionService;
    private BahmniEncounterTransactionMapper bahmniEncounterTransactionMapper;
	
	private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm";
	
	private static final String TIME_PATTERN = "HH:mm";
	
	private static final String DATE_PATTERN = "yyyy-MM-dd";
		
    @Autowired
    LocationService locationService;

    @Autowired
    ProviderService providerService;

    @Autowired
    PatientService patientService;

    @Autowired
    AppointmentServiceService appointmentServiceService;

    @Autowired
    AppointmentsService appointmentsService;
	

    public BahmniEncounterController() {
    }

    @Autowired
    public BahmniEncounterController(EncounterService encounterService,
                                     EmrEncounterService emrEncounterService, EncounterTransactionMapper encounterTransactionMapper,
                                     BahmniEncounterTransactionService bahmniEncounterTransactionService,
                                     BahmniEncounterTransactionMapper bahmniEncounterTransactionMapper) {
        this.encounterService = encounterService;
        this.emrEncounterService = emrEncounterService;
        this.encounterTransactionMapper = encounterTransactionMapper;
        this.bahmniEncounterTransactionService = bahmniEncounterTransactionService;
        this.bahmniEncounterTransactionMapper = bahmniEncounterTransactionMapper;
    }

    @RequestMapping(method = RequestMethod.GET, value = "/{uuid}")
    @ResponseBody
    public BahmniEncounterTransaction get(@PathVariable("uuid") String uuid, @RequestParam(value = "includeAll", required = false) Boolean includeAll) {
        EncounterTransaction encounterTransaction = emrEncounterService.getEncounterTransaction(uuid, includeAll);
        return bahmniEncounterTransactionMapper.map(encounterTransaction, includeAll);
    }

    @RequestMapping(method = RequestMethod.POST, value = "/find")
    @ResponseBody
    public BahmniEncounterTransaction find(@RequestBody BahmniEncounterSearchParameters encounterSearchParameters) {
        EncounterTransaction encounterTransaction = bahmniEncounterTransactionService.find(encounterSearchParameters);

        if (encounterTransaction != null) {
            return bahmniEncounterTransactionMapper.map(encounterTransaction, encounterSearchParameters.getIncludeAll());
        } else {
            return bahmniEncounterTransactionMapper.map(new EncounterTransaction(), false);
        }
    }

    @RequestMapping(method = RequestMethod.DELETE, value = "/{uuid}")
    @ResponseBody
    public void delete(@PathVariable("uuid") String uuid, @RequestParam(value = "reason", defaultValue = "web service call") String reason){
        String errorMessage = "Visit for this patient is closed. You cannot do an 'Undo Discharge' for the patient.";
        Visit visit = encounterService.getEncounterByUuid(uuid).getVisit();
        Date stopDate = visit.getStopDatetime();
        if(stopDate != null && stopDate.before(new Date())){
            throw new VisitClosedException(errorMessage);
        }
        else{
            BahmniEncounterTransaction bahmniEncounterTransaction = get(uuid,false);
            bahmniEncounterTransaction.setReason(reason);
            bahmniEncounterTransactionService.delete(bahmniEncounterTransaction);
        }
    }

    @RequestMapping(method = RequestMethod.POST)
    @ResponseBody
    @Transactional
    public BahmniEncounterTransaction update(@RequestBody BahmniEncounterTransaction bahmniEncounterTransaction) {
        setUuidsForObservations(bahmniEncounterTransaction.getObservations());
		BahmniObservation bahmniObs = getFollowUpDateObservation(bahmniEncounterTransaction.getObservations());
		
		DateFormat dateFormat = new SimpleDateFormat(DATE_PATTERN);
		Date startDate = new Date();
		Date endDate = new Date();

		if(bahmniObs != null){
			if(bahmniEncounterTransaction.getEncounterUuid() == null) {
				try {
					startDate = dateFormat.parse(bahmniObs.getValueAsString());
					endDate = dateFormat.parse(bahmniObs.getValueAsString());
					
					Appointment appointment = new Appointment();
					
					appointment.setPatient(patientService.getPatientByUuid(bahmniEncounterTransaction.getPatientUuid()));

					AppointmentService appointmentService = appointmentServiceService.getAppointmentServiceByUuid("0c8dfd62-776a-4ddd-bcee-f2570c0721fa");
					AppointmentServiceType appointmentServiceType = null;

					appointmentServiceType = getServiceTypeByUuid(appointmentService.getServiceTypes(true), "257dcd02-e539-46fb-b61c-b23e413935c2");
					appointment.setServiceType(appointmentServiceType);
					appointment.setService(appointmentService);
					
					if(!bahmniEncounterTransaction.getProviders().isEmpty())
					{
						appointment.setProvider(providerService.getProviderByUuid(bahmniEncounterTransaction.getProviders().iterator().next().getUuid()));
					}
					
					appointment.setLocation(locationService.getLocationByUuid(bahmniEncounterTransaction.getLocationUuid()));
					appointment.setStartDateTime(startDate);
					appointment.setEndDateTime(endDate);
					appointment.setAppointmentKind(AppointmentKind.valueOf("Scheduled"));
					appointment.setComments("");
				
					appointmentsService.validateAndSave(appointment);
					
				} catch (ParseException e) { 
					// Use the openmrs logger to log the exception
				}
			}
		}

        return bahmniEncounterTransactionService.save(bahmniEncounterTransaction);
    }
	
    public BahmniEncounterTransaction get(String encounterUuid) {
        Encounter encounter = encounterService.getEncounterByUuid(encounterUuid);
        boolean includeAll = false;
        EncounterTransaction encounterTransaction = encounterTransactionMapper.map(encounter, includeAll);
        return bahmniEncounterTransactionMapper.map(encounterTransaction, includeAll);
    }
	
    private AppointmentServiceType getServiceTypeByUuid(Set<AppointmentServiceType> serviceTypes, String serviceTypeUuid) {
        return serviceTypes.stream()
                .filter(avb -> avb.getUuid().equals(serviceTypeUuid)).findAny().get();
    }
}
