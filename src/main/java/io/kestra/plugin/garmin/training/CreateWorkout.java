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
    title = "Create a workout",
    description = """
        Publishes a workout to the Garmin Connect Training API. The workout can then be scheduled and synced to the
        user's compatible devices. The `workout` property is the JSON body defined by the Garmin Training API."""
)
@Plugin(
    examples = {
        @Example(
            title = "Create a running workout",
            full = true,
            code = """
                id: garmin_create_workout
                namespace: company.team

                tasks:
                  - id: create_workout
                    type: io.kestra.plugin.garmin.training.CreateWorkout
                    consumerKey: "{{ secret('GARMIN_CONSUMER_KEY') }}"
                    consumerSecret: "{{ secret('GARMIN_CONSUMER_SECRET') }}"
                    accessToken: "{{ secret('GARMIN_ACCESS_TOKEN') }}"
                    accessTokenSecret: "{{ secret('GARMIN_ACCESS_TOKEN_SECRET') }}"
                    workout:
                      workoutName: Easy 5k
                      description: Steady run at conversational pace
                      sport: RUNNING
                      segments:
                        - segmentOrder: 1
                          sport: RUNNING
                          steps:
                            - type: WorkoutStep
                              stepOrder: 1
                              intensity: ACTIVE
                              durationType: DISTANCE
                              durationValue: 5000
                              targetType: OPEN
                """
        )
    }
)
public class CreateWorkout extends AbstractGarminTask implements RunnableTask<CreateWorkout.Output> {
    private static final String PATH = "/training-api/workout";

    @Schema(
        title = "Workout definition",
        description = "The workout JSON body, as defined by the Garmin Training API (name, sport, segments and steps)."
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<Map<String, Object>> workout;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var rWorkout = runContext.render(workout).asMap(String.class, Object.class);
        if (rWorkout.isEmpty()) {
            throw new IllegalArgumentException("'workout' is required: provide the workout definition expected by the Garmin Training API");
        }

        try (var client = GarminClient.create(runContext, this)) {
            var response = client.send("POST", PATH, JacksonMapper.ofJson().writeValueAsString(rWorkout));
            var workoutId = response.get("workoutId");
            if (workoutId == null) {
                throw new IllegalStateException("Garmin did not return a 'workoutId' when creating the workout");
            }
            runContext.logger().info("Created Garmin workout {}", workoutId);
            return Output.builder().workoutId(String.valueOf(workoutId)).build();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Workout identifier",
            description = "The identifier assigned by Garmin, to use with `UpdateWorkout` and `DeleteWorkout`."
        )
        private final String workoutId;
    }
}
