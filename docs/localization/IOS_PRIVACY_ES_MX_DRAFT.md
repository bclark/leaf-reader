# Política de privacidad de Leaf — borrador en español de México

Estado: BORRADOR — pendiente de revisión por una persona que domine el español de México. No publicado; no reemplaza la política vigente en inglés.
Revisor de idioma: sin asignar.
Aprobación del propietario: pendiente.
Fecha del borrador: 6 de noviembre de 2026.
Fecha de revisión y aprobación: pendiente.
Fuente: PRIVACY.md, actualizado el 6 de noviembre de 2026.
Política vigente (en inglés): https://github.com/bclark/leaf-reader/blob/main/PRIVACY.md
Contacto y soporte vigentes (en inglés): https://github.com/bclark/leaf-reader/issues

Leaf lee y guarda tus cómics en tu dispositivo. La lectura y la importación local desde Archivos funcionan sin conexión a internet.

## Datos que recopilamos

**No recopilamos ninguno.** Leaf no tiene telemetría ni un servicio que recopile tus datos.

- Leaf no tiene telemetría, rastreo ni sincronización en la nube. Android y iOS permiten conexiones opcionales a tu propio servidor SMB/Samba. Las conexiones SMB envían solicitudes de autenticación y de archivos únicamente al servidor que hayas configurado expresamente para esa operación. No hay detección automática de servidores ni sincronización en segundo plano.
- En iOS, las contraseñas SMB se guardan en el llavero exclusivo del dispositivo, sin sincronización, y están disponibles mientras el dispositivo está desbloqueado. Las descargas completadas se importan de forma local y se pueden seguir leyendo sin conexión de red.
- Los cómics que importas se copian en el almacenamiento privado de la app en tu dispositivo y solo Leaf puede leerlos.
- El avance de lectura, los metadatos de la biblioteca y los ajustes se guardan únicamente de forma local en tu dispositivo.
- Cuando una versión incluye detección de paneles, esta se ejecuta por completo en el dispositivo. El paquete de iOS para App Store excluye el modelo. Nunca se suben imágenes de páginas ni datos derivados a ningún destino.
- Leaf no incluye analítica, reportes de fallos, publicidad ni SDK de rastreo de terceros.

## Uso compartido de datos

Leaf no nos envía tu biblioteca, tu actividad de lectura ni tus ajustes. Las importaciones opcionales por SMB se comunican con el servidor que elijas; las propinas opcionales de iOS usan los servicios de compra de Apple, como se describe a continuación.

## Propinas opcionales en iOS

Leaf ofrece propinas opcionales mediante el sistema de compras dentro de la app de Apple. Son compras únicas que puedes repetir. Todas las funciones de la app siguen siendo gratuitas, y una propina no desbloquea contenido ni privilegios. Cargar las opciones de propina y realizar una compra requiere acceso a los servicios de Apple; la lectura, las importaciones desde Archivos, los ajustes y los respaldos no dependen de compras ni de una conexión.

Apple procesa los pagos y la información de las compras conforme a su propia política de privacidad. Leaf usa StoreKit en tu dispositivo para verificar y finalizar las transacciones de propinas, incluidas las compras que se completan después de una interrupción. Leaf no envía recibos ni detalles de transacciones a un servidor administrado por el desarrollador y no agrega analítica. El identificador de producto de una propina pendiente puede guardarse de forma local para mostrar su estado; no es un registro de tu biblioteca ni de tus datos de pago. Apple conserva sus registros de compras independientemente de que desinstales Leaf.

## Eliminación de datos

Al desinstalar la app se eliminan las copias de cómics importados, las portadas, el avance de lectura y los ajustes de la app. Los elementos del llavero de iOS pueden permanecer después de desinstalarla; al quitar una conexión SMB, es necesario eliminar expresamente su contraseña guardada. Usa la opción para olvidar la conexión («Forget Connection») en los ajustes de la unidad SMB para borrar la contraseña guardada.

## Enlaces externos

El menú de la app contiene un enlace a la página del proyecto en GitHub. Al abrirlo, se abre tu navegador; a partir de ese momento se aplican las condiciones de privacidad habituales de GitHub.

## Menores de edad

Leaf no recopila datos de ninguna persona, incluidos los menores de edad.

## Cambios

Cualquier cambio en esta política se publicará en la dirección de la política vigente indicada arriba, junto con el código fuente de la app.

## Contacto

Si tienes preguntas, abre un reporte en https://github.com/bclark/leaf-reader/issues.
