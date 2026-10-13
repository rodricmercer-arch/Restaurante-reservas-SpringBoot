package com.restaurante.reservas.service;

import com.restaurante.reservas.dto.UsuarioFormDTO;
import com.restaurante.reservas.model.Rol;
import com.restaurante.reservas.model.Usuario;
import com.restaurante.reservas.repository.ReservaRepository;
import com.restaurante.reservas.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
// Por defecto todo es de solo lectura (transacción más liviana y sin flush accidental); crear/actualizar/eliminar llevan su propio @Transactional.
@Transactional(readOnly = true)
public class UsuarioService {

    private static final int PASSWORD_MINIMO = 8;

    private final UsuarioRepository usuarioRepository;
    private final ReservaRepository reservaRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository,
                          ReservaRepository reservaRepository,
                          PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.reservaRepository = reservaRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<Usuario> listar() {
        return usuarioRepository.findAllByOrderByNombreAsc();
    }

    public Optional<Usuario> buscarPorId(Long id) {
        return usuarioRepository.findById(id);
    }

    @Transactional
    public Usuario crear(UsuarioFormDTO form) {
        validarPassword(form.getPassword(), true);
        if (usuarioRepository.existsByDni(form.getDni())) {
            throw new IllegalStateException("Ya existe un usuario con el DNI " + form.getDni() + ".");
        }
        Usuario u = new Usuario(form.getDni(), passwordEncoder.encode(form.getPassword()),
                form.getNombre(), form.getRol());
        return usuarioRepository.save(u);
    }

    @Transactional
    public Usuario actualizar(UsuarioFormDTO form) {
        Usuario u = usuarioRepository.findById(form.getId())
                .orElseThrow(() -> new IllegalStateException("El usuario que intenta editar no existe."));

        boolean degradaAdmin = u.getRol() == Rol.ADMIN && form.getRol() != Rol.ADMIN;
        if (degradaAdmin && usuarioRepository.countByRol(Rol.ADMIN) <= 1) {
            throw new IllegalStateException("No se puede cambiar el rol: es el último administrador.");
        }

        boolean cambiaPassword = form.getPassword() != null && !form.getPassword().isBlank();
        if (cambiaPassword) {
            validarPassword(form.getPassword(), false);
            u.setPassword(passwordEncoder.encode(form.getPassword()));
        }
        u.setNombre(form.getNombre());
        u.setRol(form.getRol());
        return usuarioRepository.save(u);
    }

    @Transactional
    public void eliminar(Long id, String dniActual) {
        Usuario u = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("El usuario que intenta eliminar no existe."));

        if (u.getDni().equals(dniActual)) {
            throw new IllegalStateException("No puede eliminar su propia cuenta.");
        }
        if (reservaRepository.existsByClienteId(id)) {
            throw new IllegalStateException("No se puede eliminar el usuario: tiene reservas registradas.");
        }
        if (u.getRol() == Rol.ADMIN && usuarioRepository.countByRol(Rol.ADMIN) <= 1) {
            throw new IllegalStateException("No se puede eliminar al último administrador.");
        }
        usuarioRepository.delete(u);
    }

    private void validarPassword(String password, boolean obligatoria) {
        if (password == null || password.isBlank()) {
            if (obligatoria) {
                throw new IllegalStateException("La contraseña es obligatoria.");
            }
            return;
        }
        if (password.length() < PASSWORD_MINIMO) {
            throw new IllegalStateException("La contraseña debe tener al menos " + PASSWORD_MINIMO + " caracteres.");
        }
    }
}