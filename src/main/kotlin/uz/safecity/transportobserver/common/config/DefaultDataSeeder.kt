package uz.safecity.transportobserver.common.config

import uz.safecity.transportobserver.checkpointtypes.entity.CheckpointType
import uz.safecity.transportobserver.checkpointtypes.repository.CheckpointTypeRepository
import uz.safecity.transportobserver.positions.entity.EmployeePosition
import uz.safecity.transportobserver.positions.repository.EmployeePositionRepository
import uz.safecity.transportobserver.regions.entity.Region
import uz.safecity.transportobserver.regions.repository.RegionRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class DefaultDataSeeder(
	private val regionRepository: RegionRepository,
	private val employeePositionRepository: EmployeePositionRepository,
	private val checkpointTypeRepository: CheckpointTypeRepository
) : ApplicationRunner {

	private val log = LoggerFactory.getLogger(DefaultDataSeeder::class.java)

	private val defaultRegions = listOf(
		Region("Toshkent shahri", "TAS"),
		Region("Toshkent viloyati", "TV"),
		Region("Samarqand viloyati", "SAM"),
		Region("Farg'ona viloyati", "FER"),
		Region("Andijon viloyati", "AND"),
		Region("Namangan viloyati", "NAM"),
		Region("Buxoro viloyati", "BUX"),
		Region("Qashqadaryo viloyati", "QAS"),
		Region("Surxondaryo viloyati", "SUR"),
		Region("Xorazm viloyati", "XOR"),
		Region("Jizzax viloyati", "JIZ"),
		Region("Navoiy viloyati", "NAV"),
		Region("Sirdaryo viloyati", "SIR"),
		Region("Qoraqalpog'iston Respublikasi", "QR")
	)

	private val defaultPositions = listOf(
		EmployeePosition("Katta inspektor", "Senior inspection officer"),
		EmployeePosition("Inspektor", "Inspection officer"),
		EmployeePosition("Operator", "System monitoring operator"),
		EmployeePosition("Navbatchi operator", "Duty monitoring operator"),
		EmployeePosition("Boshliq", "Head / Chief officer")
	)

	/** TZ mockup spec's 4 baseline checkpoint categories — see [CheckpointType] kdoc. */
	private val defaultCheckpointTypes = listOf(
		CheckpointType("Avtovokzal", "Bus station"),
		CheckpointType("Temiryo'l vokzali", "Railway station"),
		CheckpointType("Magistral yo'l", "Highway"),
		CheckpointType("Aeroport", "Airport")
	)

	@Transactional
	override fun run(args: ApplicationArguments) {
		seedRegions()
		seedPositions()
		seedCheckpointTypes()
	}

	private fun seedRegions() {
		if (regionRepository.count() == 0L) {
			regionRepository.saveAll(defaultRegions)
			log.info("Default {} ta hudud tizimga muvaffaqiyatli kiritildi.", defaultRegions.size)
		}
	}

	private fun seedPositions() {
		if (employeePositionRepository.count() == 0L) {
			employeePositionRepository.saveAll(defaultPositions)
			log.info("Default {} ta lavozim tizimga muvaffaqiyatli kiritildi.", defaultPositions.size)
		}
	}

	private fun seedCheckpointTypes() {
		if (checkpointTypeRepository.count() == 0L) {
			checkpointTypeRepository.saveAll(defaultCheckpointTypes)
			log.info("Default {} ta nazorat punkti turi tizimga muvaffaqiyatli kiritildi.", defaultCheckpointTypes.size)
		}
	}
}
