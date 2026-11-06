-- =====================================================================
-- V3 · Product catalog seed (pharmacy items).
-- A few products have stock 0-3 on purpose, to demonstrate the configurable
-- minimum-stock filter (app.product.min-stock).
-- uuidv7() is built into PostgreSQL 18.
-- =====================================================================

INSERT INTO products (id, sku, name, description, price, stock)
VALUES (uuidv7(), 'MED-0001', 'Acetaminofén 500 mg x 20 tabletas', 'Analgésico y antipirético para dolor leve a moderado y fiebre.', 3.50, 120),
       (uuidv7(), 'MED-0002', 'Acetaminofén 650 mg x 10 tabletas', 'Analgésico y antipirético de mayor concentración.', 2.90, 80),
       (uuidv7(), 'MED-0003', 'Ibuprofeno 400 mg x 10 cápsulas', 'Antiinflamatorio no esteroideo para dolor e inflamación.', 4.20, 95),
       (uuidv7(), 'MED-0004', 'Ibuprofeno 600 mg x 10 tabletas', 'Antiinflamatorio para dolor muscular y articular.', 5.10, 2),
       (uuidv7(), 'MED-0005', 'Loratadina 10 mg x 10 tabletas', 'Antihistamínico para rinitis alérgica y urticaria.', 3.80, 60),
       (uuidv7(), 'MED-0006', 'Cetirizina 10 mg x 10 tabletas', 'Antihistamínico de segunda generación.', 4.00, 45),
       (uuidv7(), 'MED-0007', 'Omeprazol 20 mg x 14 cápsulas', 'Inhibidor de la bomba de protones para acidez y reflujo.', 6.50, 70),
       (uuidv7(), 'MED-0008', 'Diclofenac potásico 50 mg x 20 tabletas', 'Antiinflamatorio para dolor agudo.', 4.75, 0),
       (uuidv7(), 'MED-0009', 'Amoxicilina 500 mg x 21 cápsulas', 'Antibiótico de amplio espectro (requiere récipe).', 9.90, 30),
       (uuidv7(), 'MED-0010', 'Sales de rehidratación oral sabor naranja x 4 sobres', 'Reposición de electrolitos en casos de deshidratación.', 2.40, 150),
       (uuidv7(), 'MED-0011', 'Jarabe para la tos expectorante 120 ml', 'Ayuda a eliminar la flema en tos productiva.', 7.30, 40),
       (uuidv7(), 'MED-0012', 'Antigripal día x 12 tabletas', 'Alivio de síntomas de gripe y resfriado.', 5.60, 3),
       (uuidv7(), 'MED-0013', 'Loperamida 2 mg x 6 tabletas', 'Antidiarreico de acción rápida.', 3.20, 55),
       (uuidv7(), 'MED-0014', 'Ácido acetilsalicílico 100 mg x 30 tabletas', 'Antiagregante plaquetario en dosis baja.', 3.95, 65),
       (uuidv7(), 'VIT-0001', 'Vitamina C 1000 mg x 30 tabletas efervescentes', 'Suplemento de vitamina C para el sistema inmune.', 8.40, 110),
       (uuidv7(), 'VIT-0002', 'Vitamina D3 2000 UI x 60 cápsulas', 'Suplemento para la salud ósea.', 11.90, 35),
       (uuidv7(), 'VIT-0003', 'Complejo B x 30 tabletas', 'Vitaminas del complejo B para energía y sistema nervioso.', 6.80, 50),
       (uuidv7(), 'VIT-0004', 'Multivitamínico adultos x 30 tabletas', 'Vitaminas y minerales esenciales de uso diario.', 13.50, 25),
       (uuidv7(), 'VIT-0005', 'Omega 3 1000 mg x 60 cápsulas blandas', 'Ácidos grasos EPA y DHA para la salud cardiovascular.', 14.20, 1),
       (uuidv7(), 'VIT-0006', 'Zinc 50 mg x 30 tabletas', 'Suplemento mineral para el sistema inmune.', 5.90, 40),
       (uuidv7(), 'CUI-0001', 'Protector solar FPS 50 x 120 ml', 'Protección UVA/UVB de amplio espectro, resistente al agua.', 18.90, 45),
       (uuidv7(), 'CUI-0002', 'Crema hidratante corporal x 400 ml', 'Hidratación profunda para piel seca.', 9.70, 60),
       (uuidv7(), 'CUI-0003', 'Gel antibacterial x 500 ml', 'Desinfectante de manos con alcohol al 70%.', 4.30, 200),
       (uuidv7(), 'CUI-0004', 'Champú anticaspa x 400 ml', 'Control de la caspa y la picazón del cuero cabelludo.', 8.90, 38),
       (uuidv7(), 'CUI-0005', 'Crema dental con flúor x 100 ml', 'Protección anticaries y aliento fresco.', 2.80, 140),
       (uuidv7(), 'CUI-0006', 'Cepillo dental suave', 'Cerdas suaves para encías sensibles.', 2.20, 90),
       (uuidv7(), 'CUI-0007', 'Enjuague bucal sin alcohol x 500 ml', 'Elimina el 99% de los gérmenes, sin ardor.', 6.40, 0),
       (uuidv7(), 'CUI-0008', 'Desodorante roll-on sin aluminio x 50 ml', 'Protección 48 horas para piel sensible.', 4.60, 75),
       (uuidv7(), 'BEB-0001', 'Pañales etapa 3 x 30 unidades', 'Pañales desechables ultra absorbentes, talla M.', 15.90, 50),
       (uuidv7(), 'BEB-0002', 'Toallitas húmedas para bebé x 80 unidades', 'Sin alcohol ni fragancia, hipoalergénicas.', 3.70, 85),
       (uuidv7(), 'BEB-0003', 'Fórmula infantil etapa 1 x 400 g', 'Fórmula láctea para lactantes de 0 a 6 meses.', 21.50, 20),
       (uuidv7(), 'BEB-0004', 'Crema antipañalitis x 60 g', 'Previene y alivia la dermatitis del pañal.', 5.30, 3),
       (uuidv7(), 'PRI-0001', 'Termómetro digital', 'Medición de temperatura en 60 segundos.', 7.90, 30),
       (uuidv7(), 'PRI-0002', 'Alcohol isopropílico 70% x 250 ml', 'Antiséptico para uso externo.', 2.50, 130),
       (uuidv7(), 'PRI-0003', 'Curitas surtidas x 30 unidades', 'Vendas adhesivas para heridas menores.', 2.10, 160),
       (uuidv7(), 'PRI-0004', 'Gasas estériles 10 x 10 cm x 10 unidades', 'Para curación de heridas.', 1.90, 70),
       (uuidv7(), 'PRI-0005', 'Agua oxigenada 10 vol x 120 ml', 'Antiséptico para limpieza de heridas.', 1.60, 95),
       (uuidv7(), 'PRI-0006', 'Tensiómetro digital de brazo', 'Medición automática de presión arterial y pulso.', 39.90, 8),
       (uuidv7(), 'PRI-0007', 'Mascarillas quirúrgicas x 50 unidades', 'Mascarillas desechables de tres capas.', 6.90, 0),
       (uuidv7(), 'PRI-0008', 'Bolsa de agua caliente', 'Alivio del dolor muscular con calor.', 5.80, 12);
