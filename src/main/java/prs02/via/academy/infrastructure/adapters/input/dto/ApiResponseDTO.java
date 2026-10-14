package prs02.via.academy.infrastructure.adapters.input.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponseDTO<T> {

    private String message;
    private T data;

    public static <T> ApiResponseDTO<T> ok(String message, T data) {
        return ApiResponseDTO.<T>builder()
                .message(message)
                .data(data)
                .build();
    }
}
