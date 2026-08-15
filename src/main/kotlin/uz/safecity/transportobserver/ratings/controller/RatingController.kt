package uz.safecity.transportobserver.ratings.controller

import uz.safecity.transportobserver.auth.security.CustomUserDetails
import uz.safecity.transportobserver.common.dto.ApiResponse
import uz.safecity.transportobserver.ratings.dto.InspectorRatingDto
import uz.safecity.transportobserver.ratings.dto.MyRatingDto
import uz.safecity.transportobserver.ratings.dto.RatingSnapshotDto
import uz.safecity.transportobserver.ratings.service.RatingService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Inspector leaderboard (TZ section 7). See [RatingService] kdoc for the ranking metric. */
@RestController
@RequestMapping("/api/v1/ratings")
class RatingController(
	private val ratingService: RatingService
) {

	// All 4 roles may view the general leaderboard — an INSPECTOR should be able to see where
	// their peers rank, not just their own number; SUPER_ADMIN/ADMIN/OPERATOR use it for oversight.
	// Same "every role reads, scoping/absence of scoping happens in the service" shape as
	// InspectionController.list.
	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR', 'ROLE_INSPECTOR')")
	@GetMapping("/top")
	fun top(): ResponseEntity<ApiResponse<List<InspectorRatingDto>>> =
		ResponseEntity.ok(ApiResponse.ok(ratingService.getTop()))

	// Every role may call this — RatingService#getMyRating returns null (-> 204) for any
	// non-INSPECTOR caller, since an ADMIN/OPERATOR/SUPER_ADMIN has no personal inspection stats
	// to report. Kept as a 200-vs-204 distinction (not a 403) because "you have no rating" isn't
	// a permission error.
	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR', 'ROLE_INSPECTOR')")
	@GetMapping("/me")
	fun me(@AuthenticationPrincipal principal: CustomUserDetails): ResponseEntity<ApiResponse<MyRatingDto>> {
		val rating = ratingService.getMyRating(principal) ?: return ResponseEntity.noContent().build()
		return ResponseEntity.ok(ApiResponse.ok(rating))
	}

	// Same role set / 204-for-non-INSPECTOR contract as #me — see RatingService#getMyRatingHistory kdoc.
	@PreAuthorize("hasAnyAuthority('ROLE_SUPER_ADMIN', 'ROLE_ADMIN', 'ROLE_OPERATOR', 'ROLE_INSPECTOR')")
	@GetMapping("/me/history")
	fun myHistory(@AuthenticationPrincipal principal: CustomUserDetails): ResponseEntity<ApiResponse<List<RatingSnapshotDto>>> {
		val history = ratingService.getMyRatingHistory(principal) ?: return ResponseEntity.noContent().build()
		return ResponseEntity.ok(ApiResponse.ok(history))
	}
}
