package com.eureka.ationserver.service;

import com.eureka.ationserver.advice.exception.ForbiddenException;
import com.eureka.ationserver.dto.pin.PinRequest;
import com.eureka.ationserver.dto.pin.PinResponse;
import com.eureka.ationserver.model.insight.Insight;
import com.eureka.ationserver.model.insight.InsightSubCategory;
import com.eureka.ationserver.model.insight.Pin;
import com.eureka.ationserver.model.insight.PinBoard;
import com.eureka.ationserver.model.insight.PinTag;
import com.eureka.ationserver.model.persona.Persona;
import com.eureka.ationserver.model.user.User;
import com.eureka.ationserver.repository.insight.InsightRepository;
import com.eureka.ationserver.repository.insight.PinBoardRepository;
import com.eureka.ationserver.repository.insight.PinRepository;
import com.eureka.ationserver.repository.insight.PinTagRepository;
import com.eureka.ationserver.repository.persona.PersonaRepository;
import com.eureka.ationserver.utils.image.ImageUtil;
import com.eureka.ationserver.utils.parse.Parse;
import com.eureka.ationserver.utils.parse.ParseUtil;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class PinService {

  private final InsightRepository insightRepository;
  private final PinBoardRepository pinBoardRepository;
  private final PinTagRepository pinTagRepository;
  private final PinRepository pinRepository;
  private final PersonaRepository personaRepository;
  private final UserService userService;

  @Transactional
  public PinResponse.IdOut saveNewPin(PinRequest.CreateInsightIn in) throws Exception {

    PinBoard pinBoard = pinBoardRepository.getById(in.getPinBoardId());

    Parse parse = ParseUtil.parse(Pin.PIN_PREFIX, in.getUrl());

    Insight insight = Insight.builder()
        .url(in.getUrl())
        .title(parse.getTitle())
        .description(parse.getDescription())
        .imgPath(parse.getImageUrl())
        .siteName(parse.getSiteName())
        .icon(parse.getIcon())
        .insightMainCategory(null)
        .insightSubCategoryList(null)
        .open(false)
        .build();

    insightRepository.save(insight);
    pinBoard.setImgPath(insight.getImgPath());

    // save InsightPin
    Pin pin = Pin.builder()
        .pinBoard(pinBoard)
        .insight(insight)
        .pinImgPath(insight.getImgPath())
        .build();
    Pin saved = pinRepository.save(pin);

    // save Pin tag
    for (String name : in.getTagList()) {
      PinTag pinTag = PinTag.builder()
          .pin(pin)
          .name(name)
          .build();
      pinTagRepository.save(pinTag);
    }

    return PinResponse.toIdOut(saved.getId());


  }


  @Transactional
  public PinResponse.IdOut pinUp(PinRequest.FromInsightIn in) {
    User user = userService.auth();
    PinBoard pinBoard = pinBoardRepository.getById(in.getPinBoardId());
    if (user.getId() != pinBoard.getPersona().getUser().getId()) {
      throw new ForbiddenException();
    } else {
      Insight insight = insightRepository.getById(in.getInsightId());
      Pin pin = in.toPin(pinBoard, insight);
      Pin saved = pinRepository.save(pin);
      // Copy Tags
      PinTag pinTag = PinTag.builder()
          .pin(pin)
          .name(insight.getInsightMainCategory().getName())
          .build();
      pinTagRepository.save(pinTag);
      for (InsightSubCategory insightSubCategory : insight.getInsightSubCategoryList()) {
        PinTag tag = PinTag.builder()
            .pin(pin)
            .name(insightSubCategory.getSubCategory().getName())
            .build();
        pinTagRepository.save(tag);

      }
      pinBoard.setImgPath(insight.getImgPath());
      return PinResponse.toIdOut(saved.getId());
    }

  }

  @Transactional
  public PinResponse.IdOut update(PinRequest.UpdateIn in, Long pinId) {
    User user = userService.auth();
    Pin pin = pinRepository.getById(pinId);
    PinBoard orgPinBoard = pin.getPinBoard();
    if (user.getId() != pin.getPinBoard().getPersona().getUser().getId()) {
      throw new ForbiddenException();
    } else {
      PinBoard pinBoard = pinBoardRepository.getById(in.getPinBoardId());
      if (user.getId() != pinBoard.getPersona().getUser().getId()) {
        throw new ForbiddenException();
      } else {

        if (orgPinBoard.getId() != pinBoard.getId()) {
          pinTagRepository.deleteByPin_Id(pinId);
          for (String name : in.getTagList()) {
            PinTag pinTag = PinTag.builder()
                .pin(pin)
                .name(name)
                .build();
            pinTagRepository.save(pinTag);
          }
        }
        pin.setPinBoard(pinBoard);

        // change pinBoard image
        List<Pin> orgList = pinRepository.findByPinBoardOrderByModifiedAtDesc(orgPinBoard);
        if (orgList.size() == 0) {
          orgPinBoard.setImgPath(ImageUtil.getDefaultImagePath(PinBoard.PINBOARD_PREFIX));
        } else {
          orgPinBoard.setImgPath(orgList.get(0).getPinImgPath());
        }
        pinBoard.setImgPath(pin.getPinImgPath());
        return PinResponse.toIdOut(pinId);

      }
    }
  }

  @Transactional
  public PinResponse.IdOut delete(Long pinId) {
    User user = userService.auth();
    Pin pin = pinRepository.getById(pinId);
    if (user.getId() != pin.getPinBoard().getPersona().getUser().getId()) {
      throw new ForbiddenException();
    } else {
      PinBoard orgPinBoard = pin.getPinBoard();
      pinRepository.deleteById(pinId);

      // change pinBoard image
      List<Pin> orgList = pinRepository.findByPinBoardOrderByModifiedAtDesc(orgPinBoard);
      if (orgList.size() == 0) {
        orgPinBoard.setImgPath(ImageUtil.getDefaultImagePath(PinBoard.PINBOARD_PREFIX));
      } else {
        orgPinBoard.setImgPath(orgList.get(0).getPinImgPath());
      }
      return PinResponse.toIdOut(pinId);
    }
  }

  @Transactional(readOnly = true)
  public List<PinResponse.Out> findAll(Long personaId) {
    User user = userService.auth();

    Persona persona = personaRepository.getById(personaId);
    if (persona.getUser().getId() != user.getId()) {
      throw new ForbiddenException();
    } else {
      List<PinResponse.Out> outList = pinRepository.findByPinBoard_Persona(persona).stream()
          .map(PinResponse::toOut).collect(Collectors.toList());
      return outList;
    }

  }

  @Transactional(readOnly = true)
  public PinResponse.Out find(Long pinId) {
    User user = userService.auth();

    Pin pin = pinRepository.getById(pinId);
    if (user.getId() != pin.getPinBoard().getPersona().getUser().getId()) {
      throw new ForbiddenException();
    } else {
      return PinResponse.toOut(pin);
    }
  }

  @Transactional(readOnly = true)
  public Set<PinResponse.Out> search(Long personaId, String keyword) {
    User user = userService.auth();

    Persona persona = personaRepository.getById(personaId);
    if (persona.getUser().getId() != user.getId()) {
      throw new ForbiddenException();
    }
    Set<Pin> pins1 = pinRepository.findByPinBoard_PersonaAndInsight_TitleContainingOrderByCreatedAtDesc(
        persona, keyword);
    Set<Pin> pins2 = pinRepository.findByPinTagList_NameContainingOrderByCreatedAtDesc(keyword);
    for (Pin pin : pins2) {
      if (pin.getPinBoard().getPersona().getId() == personaId) {
        pins1.add(pin);
      }
    }
    Set<PinResponse.Out> outSet = new HashSet<>();
    for (Pin pin : pins1) {
      outSet.add(PinResponse.toOut(pin));
    }

    return outSet;
  }

  @Transactional(readOnly = true)
  public List<PinResponse.Out> findByPinBoard(Long pinBoardId) {
    User user = userService.auth();

    PinBoard pinBoard = pinBoardRepository.getById(pinBoardId);
    if (pinBoard.getPersona().getUser().getId() != user.getId()) {
      throw new ForbiddenException();
    }
    List<PinResponse.Out> outList = pinRepository.findByPinBoard(pinBoard).stream()
        .map(PinResponse::toOut).collect(Collectors.toList());
    return outList;
  }


  @Transactional
  public PinResponse.Out saveImg(Long pinId, MultipartFile pinImg) throws IOException {
    Pin pin = pinRepository.getById(pinId);
    List<String> pathList = ImageUtil.getImagePath(Pin.PIN_PREFIX, pinId);
    File file = new File(pathList.get(1));
    pinImg.transferTo(file);
    pin.setPinImgPath(pathList.get(0));

    PinBoard pinBoard = pin.getPinBoard();

    pinBoard.setImgPath(pin.getPinImgPath());

    return PinResponse.toOut(pin);
  }
}
