package com.example.demo

import com.example.demo.dto.BookAppointmentRequest
import com.example.demo.exception.DuplicateResourceException
import com.example.demo.model.*
import com.example.demo.repository.*
import com.example.demo.service.AppointmentService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ContextConfiguration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
@ContextConfiguration(initializers = [DotEnvTestInitializer::class])
class ConcurrentAppointmentBookingTest {

    @Autowired
    private lateinit var appointmentService: AppointmentService

    @Autowired
    private lateinit var appointmentRepository: AppointmentRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var clinicRepository: ClinicRepository

    @Autowired
    private lateinit var clinicDoctorRepository: ClinicDoctorRepository

    @Autowired
    private lateinit var specialtyRepository: SpecialtyRepository

    @Autowired
    private lateinit var clinicSpecialtyRepository: ClinicSpecialtyRepository

    @Autowired
    private lateinit var scheduleRepository: ScheduleRepository

    private lateinit var testClinic: Clinic
    private lateinit var testDoctor: User
    private lateinit var otherDoctor: User
    private lateinit var testPatient1: User
    private lateinit var testPatient2: User
    private lateinit var specialty60Min: Specialty
    private lateinit var specialty30Min: Specialty

    private val bookingDate: LocalDate = LocalDate.now().plusDays(4)

    @BeforeEach
    fun setup() {
        appointmentRepository.deleteAll()
        scheduleRepository.deleteAll()
        clinicSpecialtyRepository.deleteAll()
        clinicDoctorRepository.deleteAll()

        val randomSuffix = UUID.randomUUID().toString().substring(0, 8)

        val clinicUser = userRepository.save(
            User(
                fullName = "Care Dental Clinic",
                email = "clinic_$randomSuffix@test.com",
                password = "Password@123",
                role = Role.CLINIC,
                city = City.AMMAN,
                isActive = true
            )
        )
        testClinic = clinicRepository.save(
            Clinic(
                user = clinicUser,
                clinicName = "Care Dental",
                applicationStatus = ClinicApplicationStatus.APPROVED
            )
        )

        testDoctor = userRepository.save(
            User(
                fullName = "Dr. Sameer",
                email = "doc1_$randomSuffix@test.com",
                password = "Password@123",
                role = Role.DOCTOR,
                city = City.AMMAN,
                isActive = true
            )
        )
        otherDoctor = userRepository.save(
            User(
                fullName = "Dr. Layla",
                email = "doc2_$randomSuffix@test.com",
                password = "Password@123",
                role = Role.DOCTOR,
                city = City.AMMAN,
                isActive = true
            )
        )

        testPatient1 = userRepository.save(
            User(
                fullName = "Patient One",
                email = "pat1_$randomSuffix@test.com",
                password = "Password@123",
                role = Role.PATIENT,
                city = City.AMMAN,
                isActive = true
            )
        )
        testPatient2 = userRepository.save(
            User(
                fullName = "Patient Two",
                email = "pat2_$randomSuffix@test.com",
                password = "Password@123",
                role = Role.PATIENT,
                city = City.AMMAN,
                isActive = true
            )
        )

        clinicDoctorRepository.save(ClinicDoctor(ClinicDoctorId(testDoctor.id!!, clinicUser.id!!), testDoctor, clinicUser))
        clinicDoctorRepository.save(ClinicDoctor(ClinicDoctorId(otherDoctor.id!!, clinicUser.id!!), otherDoctor, clinicUser))

        specialty60Min = specialtyRepository.save(Specialty(name = "RootCanal_$randomSuffix"))
        specialty30Min = specialtyRepository.save(Specialty(name = "Checkup_$randomSuffix"))

        clinicSpecialtyRepository.save(ClinicSpecialty(ClinicSpecialtyId(testClinic.id!!, specialty60Min.id!!), testClinic, specialty60Min, 60))
        clinicSpecialtyRepository.save(ClinicSpecialty(ClinicSpecialtyId(testClinic.id!!, specialty30Min.id!!), testClinic, specialty30Min, 30))

        val day = bookingDate.dayOfWeek
        scheduleRepository.save(Schedule(type = ScheduleType.CLINIC_HOURS, clinic = testClinic, dayOfWeek = day, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(18, 0)))
        scheduleRepository.save(Schedule(type = ScheduleType.DOCTOR_SHIFT, clinic = testClinic, doctor = testDoctor, dayOfWeek = day, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(18, 0)))
        scheduleRepository.save(Schedule(type = ScheduleType.DOCTOR_SHIFT, clinic = testClinic, doctor = otherDoctor, dayOfWeek = day, startTime = LocalTime.of(8, 0), endTime = LocalTime.of(18, 0)))
    }

    @Test
    fun `concurrent overlapping booking requests for same doctor result in exactly one winner and one conflict`() {
        val executor = Executors.newFixedThreadPool(2)
        val readyGate = CountDownLatch(2)
        val startGate = CountDownLatch(1)

        val successCount = AtomicInteger(0)
        val conflictCount = AtomicInteger(0)

        // Request 1: 09:00 - 10:00 (60 minutes)
        val req1 = BookAppointmentRequest(
            clinicId = testClinic.id!!,
            doctorId = testDoctor.id!!,
            patientName = "Patient One",
            patientAge = 25,
            serviceIds = listOf(specialty60Min.id!!),
            appointmentAt = LocalDateTime.of(bookingDate, LocalTime.of(9, 0)),
            paymentMethod = "CASH"
        )

        // Request 2: 09:15 - 09:45 (30 minutes - overlaps 09:00 - 10:00)
        val req2 = BookAppointmentRequest(
            clinicId = testClinic.id!!,
            doctorId = testDoctor.id!!,
            patientName = "Patient Two",
            patientAge = 30,
            serviceIds = listOf(specialty30Min.id!!),
            appointmentAt = LocalDateTime.of(bookingDate, LocalTime.of(9, 15)),
            paymentMethod = "CASH"
        )

        executor.submit {
            readyGate.countDown()
            startGate.await()
            try {
                appointmentService.bookAppointment(testPatient1.email, req1)
                successCount.incrementAndGet()
            } catch (ex: DuplicateResourceException) {
                conflictCount.incrementAndGet()
            } catch (ex: Exception) {
                if (ex.cause is DuplicateResourceException) conflictCount.incrementAndGet()
            }
        }

        executor.submit {
            readyGate.countDown()
            startGate.await()
            try {
                appointmentService.bookAppointment(testPatient2.email, req2)
                successCount.incrementAndGet()
            } catch (ex: DuplicateResourceException) {
                conflictCount.incrementAndGet()
            } catch (ex: Exception) {
                if (ex.cause is DuplicateResourceException) conflictCount.incrementAndGet()
            }
        }

        readyGate.await()
        startGate.countDown()
        executor.shutdown()
        executor.awaitTermination(10, TimeUnit.SECONDS)

        assertEquals(1, successCount.get(), "Exactly one concurrent booking request must succeed")
        assertEquals(1, conflictCount.get(), "The overlapping concurrent request must be rejected with DuplicateResourceException")

        val persisted = appointmentRepository.findAll().count { it.status != AppointmentStatus.CANCELLED }
        assertEquals(1, persisted, "Only one appointment must be persisted in database")
    }

    @Test
    fun `concurrent bookings for different doctors at identical times both succeed`() {
        val executor = Executors.newFixedThreadPool(2)
        val readyGate = CountDownLatch(2)
        val startGate = CountDownLatch(1)

        val successCount = AtomicInteger(0)

        val reqDoc1 = BookAppointmentRequest(
            clinicId = testClinic.id!!,
            doctorId = testDoctor.id!!,
            patientName = "Patient 1",
            patientAge = 22,
            serviceIds = listOf(specialty60Min.id!!),
            appointmentAt = LocalDateTime.of(bookingDate, LocalTime.of(11, 0)),
            paymentMethod = "CASH"
        )
        val reqDoc2 = BookAppointmentRequest(
            clinicId = testClinic.id!!,
            doctorId = otherDoctor.id!!,
            patientName = "Patient 2",
            patientAge = 28,
            serviceIds = listOf(specialty60Min.id!!),
            appointmentAt = LocalDateTime.of(bookingDate, LocalTime.of(11, 0)),
            paymentMethod = "CASH"
        )

        executor.submit {
            readyGate.countDown()
            startGate.await()
            appointmentService.bookAppointment(testPatient1.email, reqDoc1)
            successCount.incrementAndGet()
        }

        executor.submit {
            readyGate.countDown()
            startGate.await()
            appointmentService.bookAppointment(testPatient2.email, reqDoc2)
            successCount.incrementAndGet()
        }

        readyGate.await()
        startGate.countDown()
        executor.shutdown()
        executor.awaitTermination(10, TimeUnit.SECONDS)

        assertEquals(2, successCount.get(), "Concurrent requests for different doctors must run without blocking each other")
    }

    @Test
    fun `adjacent appointments touching at exact boundary times are both allowed`() {
        val appt1 = BookAppointmentRequest(
            clinicId = testClinic.id!!,
            doctorId = testDoctor.id!!,
            patientName = "Adjacent One",
            patientAge = 22,
            serviceIds = listOf(specialty30Min.id!!),
            appointmentAt = LocalDateTime.of(bookingDate, LocalTime.of(10, 0)),
            paymentMethod = "CASH"
        )
        val appt2 = BookAppointmentRequest(
            clinicId = testClinic.id!!,
            doctorId = testDoctor.id!!,
            patientName = "Adjacent Two",
            patientAge = 28,
            serviceIds = listOf(specialty60Min.id!!),
            appointmentAt = LocalDateTime.of(bookingDate, LocalTime.of(10, 30)),
            paymentMethod = "CASH"
        )

        val first = appointmentService.bookAppointment(testPatient1.email, appt1)
        val second = appointmentService.bookAppointment(testPatient2.email, appt2)

        assertTrue(first.appointmentId.toString().isNotBlank())
        assertTrue(second.appointmentId.toString().isNotBlank())
    }
}