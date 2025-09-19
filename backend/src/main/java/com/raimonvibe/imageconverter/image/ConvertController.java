package com.raimonvibe.imageconverter.image;

import com.raimonvibe.imageconverter.user.User;
import com.raimonvibe.imageconverter.user.UserRepository;
import com.raimonvibe.imageconverter.user.UserService;
import com.raimonvibe.imageconverter.user.AnonymousUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.Principal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StreamUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/convert")
@Validated
public class ConvertController {

  private final ImageService imageService = new ImageService();
  private final UserRepository userRepository;
  private final UserService userService;
  private final AnonymousUserService anonymousUserService;

  @Value("${app.stripe.pricePackSize:20}")
  private int packSize;

  public ConvertController(UserRepository userRepository, UserService userService, AnonymousUserService anonymousUserService) {
    this.userRepository = userRepository;
    this.userService = userService;
    this.anonymousUserService = anonymousUserService;
  }

  @GetMapping("/formats")
  public Object formats() {
    return ImageService.supportedFormats();
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public void convert(
      @RequestParam("file") MultipartFile file,
      @RequestParam("to") @NotBlank String toFormat,
      @RequestParam(value = "quality", required = false) @Min(1) @Max(100) Integer quality,
      Principal principal,
      HttpServletRequest request,
      HttpServletResponse response
  ) throws IOException, InterruptedException {
    
    boolean allowed = false;
    
    if (principal != null) {
      String email = principal.getName();
      User user = userRepository.findByEmail(email).orElseThrow();
      allowed = userService.consumeOneConversion(user, packSize);
    } else {
      String clientIp = getClientIpAddress(request);
      allowed = anonymousUserService.consumeOneConversion(clientIp, 20);
    }
    
    if (!allowed) {
      response.sendError(402, "Payment Required");
      return;
    }

    File tmp = File.createTempFile("upload-", ".bin");
    file.transferTo(tmp);
    var out = imageService.convert(tmp, new ImageService.ConversionOptions(toFormat, quality));
    response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"converted." + toFormat + "\"");
    response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
    try (FileInputStream fis = new FileInputStream(out)) {
      StreamUtils.copy(fis, response.getOutputStream());
    } finally {
      tmp.delete();
      out.delete();
    }
  }

  private String getClientIpAddress(HttpServletRequest request) {
    String xForwardedFor = request.getHeader("X-Forwarded-For");
    if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
      return xForwardedFor.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}