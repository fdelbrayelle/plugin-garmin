package io.kestra.plugin.garmin.training;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.plugin.garmin.AbstractGarminTask;
import io.kestra.plugin.garmin.GarminClient;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Create a course",
    description = """
        Publishes a course to the Garmin Connect Courses API. The course can then be synced to the user's
        compatible devices. The `course` property is the JSON body defined by the Garmin Courses API."""
)
@Plugin(
    examples = {
        @Example(
            title = "Create a cycling course",
            full = true,
            code = """
                id: garmin_create_course
                namespace: company.team

                tasks:
                  - id: create_course
                    type: io.kestra.plugin.garmin.training.CreateCourse
                    consumerKey: "{{ secret('GARMIN_CONSUMER_KEY') }}"
                    consumerSecret: "{{ secret('GARMIN_CONSUMER_SECRET') }}"
                    accessToken: "{{ secret('GARMIN_ACCESS_TOKEN') }}"
                    accessTokenSecret: "{{ secret('GARMIN_ACCESS_TOKEN_SECRET') }}"
                    course:
                      courseName: Sunday loop
                      activityType: ROAD_CYCLING
                      geoPoints:
                        - latitude: 48.8566
                          longitude: 2.3522
                          elevation: 35
                        - latitude: 48.8606
                          longitude: 2.3376
                          elevation: 38
                """
        )
    }
)
public class CreateCourse extends AbstractGarminTask implements RunnableTask<CreateCourse.Output> {
    private static final String PATH = "/training-api/courses/v1/course";

    @Schema(
        title = "Course definition",
        description = "The course JSON body, as defined by the Garmin Courses API (name, activity type, geo points and course points)."
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<Map<String, Object>> course;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var rCourse = runContext.render(course).asMap(String.class, Object.class);
        if (rCourse.isEmpty()) {
            throw new IllegalArgumentException("'course' is required: provide the course definition expected by the Garmin Courses API");
        }

        try (var client = GarminClient.create(runContext, this)) {
            var response = client.send("POST", PATH, JacksonMapper.ofJson().writeValueAsString(rCourse));
            var courseId = response.get("courseId");
            if (courseId == null) {
                throw new IllegalStateException("Garmin did not return a 'courseId' when creating the course");
            }
            runContext.logger().info("Created Garmin course {}", courseId);
            return Output.builder().courseId(String.valueOf(courseId)).build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Course identifier",
            description = "The identifier assigned by Garmin to the course."
        )
        private final String courseId;
    }
}
