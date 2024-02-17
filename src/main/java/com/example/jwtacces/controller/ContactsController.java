package com.example.jwtacces.controller;

import com.example.jwtacces.DTO.RequestContactDTO;
import com.example.jwtacces.DTO.UserDTO;
import com.example.jwtacces.models.UserEntity;
import com.example.jwtacces.models.contact.Contact;
import com.example.jwtacces.models.contact.RequestContact;
import com.example.jwtacces.repository.UserRepository;
import com.example.jwtacces.repository.contact.ContactRepository;
import com.example.jwtacces.repository.contact.ContactRequestRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping(path = "/api2")
public class ContactsController {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ContactRepository contactRepository;

    @Autowired
    private ContactRequestRepository contactRequestRepository;


    /* obtiene un set de usuarios buscandolos por el id */
    private Set<UserDTO> getUsersById(Set<Integer> contactId) throws UsernameNotFoundException{
        Set<UserDTO>contacts = new HashSet<UserDTO>();
        for(Integer id : contactId){
            UserEntity contact = userRepository.findById(id)
                    .orElseThrow(()-> new UsernameNotFoundException("User not found"));
            UserDTO userDTO = UserDTO.builder()
                    .username(contact.getUsername())
                    .firstName(contact.getFirstName())
                    .lastName(contact.getLastName())
                    .email(contact.getEmail())
                    .build();
            contacts.add(userDTO);
        }
        return contacts;
    }

    /* obtienes al usuario que ha realizado la peticion con un jwt */
    public UserEntity getUserFromAuthentification(Authentication authentication){
        String username = authentication.getName();
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(()-> new UsernameNotFoundException("User not found"));
        return user;
    }

    /* retorna el user que se corresponde a la peticion de amistad */
    public UserEntity getUserFromRequestContactDTO(RequestContactDTO requestContactDTO){
        String usernameContact = requestContactDTO.getUsername();
        UserEntity contact = null;
        contact = userRepository.findByUsername(usernameContact)
                .orElseThrow(()-> new UsernameNotFoundException("User not found"));
        return contact;
    }

    @GetMapping(path = "/welcome")
    public String welcome() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserEntity user = getUserFromAuthentification(authentication);
        String welcome = "Hola, " + user.getFirstName() + " tu telefono es: " + user.getUsername();
        return welcome;
    }

    /* devuelve los contactos que tiene cada usuario, si no tiene devolvera null */
    @GetMapping(path = "/showContacts")
    public ResponseEntity<?> showContacts(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserEntity user = getUserFromAuthentification(authentication);
        Set<Integer>contactsId = new HashSet<Integer>();
        Set<UserDTO>contacts;
        try {
            //busca los id en la tabla contactos, solo se obtiene de info el id
            contactsId = contactRepository.findContactIdsByUserId(Long.valueOf(user.getId()))
                    .orElseThrow(() -> new EmptyResultDataAccessException(1));

            //busca info de los contactos haciendo uso de los anteriores id
            contacts = getUsersById(contactsId);

        } catch (EmptyResultDataAccessException e) {
            return ResponseEntity.badRequest().body(null);
        } catch (UsernameNotFoundException e) {
            return ResponseEntity.badRequest().body(null);
        }
        return ResponseEntity.ok().body(contacts);
    }




    /*
    realiza la logica que se encarga de mandar una soliticud a otro usuario para ser contactos, siempre y cuando
    este usuario exista, no seas tu mismo, ni haya una solicitud pendiente
     */
    @PostMapping(path = "/requestContact")
    public ResponseEntity<?> doRequestContact(@Valid @RequestBody RequestContactDTO requestContactDTO){
        Map<String, Object> httpResponse = new HashMap<>();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserEntity user = getUserFromAuthentification(authentication);
        UserEntity contact = new UserEntity();
        try{
            contact = getUserFromRequestContactDTO(requestContactDTO);
        } catch (UsernameNotFoundException e) {
            httpResponse.put("error","No puedes mandar solicitud a ese numero");
            return ResponseEntity.badRequest().body(httpResponse);
        }

        if(contact == null || user.getId() == contact.getId()){
            httpResponse.put("error","No puedes mandar solicitud a ese numero");
            return ResponseEntity.badRequest().body(httpResponse);
        }
        if (contactRepository.isAlreadyContact(Long.valueOf(user.getId()), Long.valueOf(contact.getId()))) {
            httpResponse.put("error","Ya tienes a ese usuario como contacto");
            return ResponseEntity.badRequest().body(httpResponse);
        }
        else{
            if(!contactRequestRepository.requestAlreadyExist(Long.valueOf(contact.getId()), Long.valueOf(user.getId()))){
                RequestContact requestContact = RequestContact.builder()
                        .userId(Long.valueOf(contact.getId()))
                        .userRequestId(Long.valueOf(user.getId()))
                        .accept(false)
                        .build();
                contactRequestRepository.save(requestContact);
                httpResponse.put("response","Solicitud de contacto enviada con éxito");
                return ResponseEntity.ok(httpResponse);
            }
            httpResponse.put("error","Ya has enviado una solicitud a esa persona anteriormente");
            return ResponseEntity.badRequest().body(httpResponse);
        }
    }

    /*
    devuelve la lista de usuarios que tienes pendientes de aceptar
     */
    @GetMapping(path = "/showRequestContact")
    public ResponseEntity<?> showRequestContact(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserEntity user = getUserFromAuthentification(authentication);
        Set<Integer>contactRequestSendersId = new HashSet<Integer>();
        Set<UserDTO>contacts = null;
        try {
            //busca el id de los usuarios que tienes pendiente de aceptar
            // (ya que en la tabla RequestContact solo aparece como info el id de los pendientes)
            contactRequestSendersId = contactRequestRepository.findRequestContactIdsByUserId(Long.valueOf(user.getId()))
                    .orElseThrow(() -> new EmptyResultDataAccessException(1));

            //busca info de los contactos haciendo uso de los anteriores id
            contacts = getUsersById(contactRequestSendersId);
        } catch (EmptyResultDataAccessException e) {
        } catch (UsernameNotFoundException e) {
        }

        return ResponseEntity.ok().body(contacts);
    }


    /*
    funcion que se encarga de aceptar la solicitud de contacto y marcar como aceptadas en true las filas involucradas
    si por ejemplo dados dos usuarios x e y, x tiene solicitud de contacto de y (y viceversa) ambos pasaran a ser contactos
    y las dos solicitudes de contacto estaran aceptadas y ya no apareceran como notificaciones
     */
    @Transactional
    @PostMapping(path = "/acceptRequestContact")
    public ResponseEntity<?> acceptRequestContact(@Valid @RequestBody RequestContactDTO requestContactDTO){
        Map<String, Object> httpResponse = new HashMap<>();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserEntity user = getUserFromAuthentification(authentication);
        UserEntity contact = getUserFromRequestContactDTO(requestContactDTO);
        //comprueba que el user que se manda como request no sea nulo ni sea el mismo usuario
        if(contact == null || user.getId() == contact.getId()){
            httpResponse.put("error","Error en el request");
            return ResponseEntity.badRequest().body(httpResponse);
        }
        //comprueba que no sean ya contactos
        if (contactRepository.isAlreadyContact(Long.valueOf(user.getId()), Long.valueOf(contact.getId()))) {
            httpResponse.put("error","Ya son contactos");
            return ResponseEntity.badRequest().body(httpResponse);
        }

        //actualiza las filas de la tabla en la bd aceptando la solicitud y tambien la posible solicitud que tenga el otro contacto
        //para que no se queden solicitudes pendientes
        int requestAccepted = 0;
        requestAccepted += contactRequestRepository.validateRequest(Long.valueOf(user.getId()), Long.valueOf(contact.getId()));
        requestAccepted += contactRequestRepository.validateRequest(Long.valueOf(contact.getId()), Long.valueOf(user.getId()));
        if(requestAccepted > 0){
            Contact addContact = Contact.builder()
                    .userId(Long.valueOf(user.getId()))
                    .user2Id(Long.valueOf(contact.getId()))
                    .build();
            Contact addContactInverse = Contact.builder()
                    .userId(Long.valueOf(contact.getId()))
                    .user2Id(Long.valueOf(user.getId()))
                    .build();
            contactRepository.save(addContact);
            contactRepository.save(addContactInverse);
            httpResponse.put("response","Ya son contactos");
            return ResponseEntity.ok(httpResponse);
        }
        httpResponse.put("response","Error al agregar el contacto");
        return ResponseEntity.badRequest().body(httpResponse);
    }
}
