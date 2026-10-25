package io.kestra.plugin.garmin.training;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.garmin.AbstractGarminTask;
import io.kestra.plugin.garmin.GarminClient;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Delete a workout",
    description = "Deletes a workout previously published to the Garmin Connect Training API."
)
@Plugin(
    examples = {
        @Example(
            title = "Delete a workout",
            full = true,
            code = """
                id: garmin_delete_workout
                namespace: company.team

                tasks:
                  - id: delete_workout
                    type: io.kestra.plugin.garmin.training.DeleteWorkout
                    consumerKey: "{{ secret('GARMIN_CONSUMER_KEY') }}"
                    consumerSecret: "{{ secret('GARMIN_CONSUMER_SECRET') }}"
                    accessToken: "{{ secret('GARMIN_ACCESS_TOKEN') }}"
                    accessTokenSecret: "{{ secret('GARMIN_ACCESS_TOKEN_SECRET') }}"
                    workoutId: "123456"
                """
        )
    }
)
public class DeleteWorkout extends AbstractGarminTask implements RunnableTask<VoidOutput> {
    @Schema(
        title = "Workout identifier",
        description = "The `workoutId` returned when the workout was created."
    )
    @NotNull
    @PluginProperty(group = "main")
    private Property<String> workoutId;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        var rWorkoutId = runContext.render(workoutId).as(String.class)
            .filter(v -> !v.isBlank())
            .orElseThrow(() -> new IllegalArgumentException("'workoutId' is required"));

        try (var client = GarminClient.create(runContext, this)) {
            client.send("DELETE", "/training-api/workout/" + GarminClient.pathSegment(rWorkoutId), null);
        }
        runContext.logger().info("Deleted Garmin workout {}", rWorkoutId);
        return null;
    }
}
