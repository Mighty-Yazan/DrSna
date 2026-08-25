package com.example.demo.repository

import com.example.demo.model.Schedule
import com.example.demo.model.ScheduleType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.UUID

@Repository
interface ScheduleRepository : JpaRepository<Schedule, Long> {

    fun findByClinicId(clinicId: UUID): List<Schedule>

    fun findByClinicIdAndTypeAndDayOfWeek(clinicId: UUID, type: ScheduleType, dayOfWeek: DayOfWeek): Schedule?

    fun existsByClinicIdAndSpecificDateAndType(clinicId: UUID, specificDate: LocalDate, type: ScheduleType): Boolean

    fun findAllByClinicId(clinicId: UUID): List<Schedule>
}