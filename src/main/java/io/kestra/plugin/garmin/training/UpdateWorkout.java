package io.kestra.plugin.garmin.training;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.plugin.garmin.AbstractGarminTask;
import io.kestra.plugin.garmin.GarminClient;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
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
    title = "Update a workout",
    description = "Replaces a workout previously published to the Garmin Connect Training API with the given definition."
)
@Plugin(
    examples = {
        @Example(
            title = "Update a workout",
            full = true,
            code = """
                id: garmin_update_workout
                namespace: company.team

                tasks:
                  - id: update_workout
                    type: io.kestra.plugin.garmin.training.UpdateWorkout
                    consumerKey: "{{ secret('GARMIN_CONSUMER_KEY') }}"
                    consumerSecret: "{{ secret('GARMIN_CONSUMER_SECRET') }}"
                    accessToken: "{{ secret('GARMIN_ACCESS_TOKEN') }}"
                    accessTokenSecret: "{{ secret('GARMIN_ACCESS_TOKEN_SECRET') }}"
                    workoutId: "123456"
                    workout:
                      workoutId: 123456
                      workoutName: Easy 6k
                      sport: RUNNING
                      segments:
                        - segmentOrder: 1
                          sport: RUNNING
                          steps:
                            - type: WorkoutStep
                              stepOrder: 1
                              intensity: ACTIVE
                              durationType: DISTANCE
                              durationValue: 6000
                              targetType: OPEN
                """
        )
    }
)
public class UpdateWorkout extends AbstractGarminTask implements RunnableTask<VoidOutput> {
    @Schema(
        title = "Workout identifier",
        description = "The `workoutId` returned when the workout was created."
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> workoutId;

    @Schema(
        title = "Workout definition",
        description = "The new workout JSON body, as defined by the Garmin Training API."
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<Map<String, Object>> workout;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        var rWorkoutId = runContext.render(workoutId).as(String.class)
            .filter(v -> !v.isBlank())
            .orElseThrow(() -> new IllegalArgumentException("'workoutId' is required"));
        var rWorkout = runContext.render(workout).asMap(String.class, Object.class);
        if (rWorkout.isEmpty()) {
            throw new IllegalArgumentException("'workout' is required: provide the workout definition expected by the Garmin Training API");
        }

        try (var client = GarminClient.create(runContext, this)) {
            client.send("PUT", "/training-api/workout/" + GarminClient.pathSegment(rWorkoutId), JacksonMapper.ofJson().writeValueAsString(rWorkout));
        }
        runContext.logger().info("Updated Garmin workout {}", rWorkoutId);
        return null;
    }
}
