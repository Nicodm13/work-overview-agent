package dk.school.workoverviewagent.review.api;

import dk.school.workoverviewagent.review.contract.ReviewRequest;
import dk.school.workoverviewagent.review.contract.ReviewResponse;

public interface IReviewService {

    ReviewResponse reviewWorkContext(ReviewRequest request);
}
