package com.example.demo.repository

import com.example.demo.model.Schedule
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.DayOfWeek
import java.util.UUID

@Repository
interface ScheduleRepository : JpaRepository<Schedule, UUID> {
    fun findAllByClinicIdOrderByDoctorIdAscDayOfWeekAscStartTimeAsc(clinicId: UUID): List<Schedule>
    fun findAllByClinicIdAndDoctorIdAndDayOfWeekOrderByStartTimeAsc(
        clinicId: UUID,
        doctorId: UUID,
        dayOfWeek: DayOfWeek
    ): List<Schedule>
    fun findAllByClinicIdAndDayOfWeek(clinicId: UUID, dayOfWeek: DayOfWeek): List<Schedule>
}
