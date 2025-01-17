/*
 * Copyright 2014-2022 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.atlas.json

import java.io.InputStream
import java.io.OutputStream
import java.io.Reader
import java.io.Writer

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core._
import com.fasterxml.jackson.core.json.JsonReadFeature
import com.fasterxml.jackson.core.json.JsonWriteFeature
import com.fasterxml.jackson.databind._
import com.fasterxml.jackson.dataformat.smile.SmileFactory
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.scala.DefaultScalaModule

object Json {

  final class Decoder[T: Manifest](reader: ObjectReader, factory: JsonFactory) {

    def decode(json: Array[Byte]): T = decode(factory.createParser(json))

    def decode(json: Array[Byte], offset: Int, length: Int): T =
      decode(factory.createParser(json, offset, length))

    def decode(json: String): T = decode(factory.createParser(json))

    def decode(input: Reader): T = decode(factory.createParser(input))

    def decode(input: InputStream): T = decode(factory.createParser(input))

    def decode(node: JsonNode): T = reader.readValue[T](node)

    def decode(parser: JsonParser): T = {
      try {
        val value = reader.readValue[T](parser)
        require(parser.nextToken() == null, "invalid json, additional content after value")
        value
      } finally {
        parser.close()
      }
    }
  }

  private val jsonFactory = JsonFactory
    .builder()
    .asInstanceOf[JsonFactoryBuilder]
    .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
    .enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS)
    .enable(StreamReadFeature.AUTO_CLOSE_SOURCE)
    .enable(StreamWriteFeature.AUTO_CLOSE_TARGET)
    .enable(JsonWriteFeature.WRITE_NAN_AS_STRINGS)
    .build()

  private val smileFactory = SmileFactory
    .builder()
    .enable(StreamReadFeature.AUTO_CLOSE_SOURCE)
    .enable(StreamWriteFeature.AUTO_CLOSE_TARGET)
    .build()

  private val jsonMapper = newMapper(jsonFactory)

  private val smileMapper = newMapper(smileFactory)

  private def newMapper(factory: JsonFactory): ObjectMapper = {
    val mapper = new ObjectMapper(factory)
    mapper.setSerializationInclusion(JsonInclude.Include.NON_ABSENT)
    mapper.configure(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS, false)
    mapper.configure(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS, false)
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    mapper.registerModule(DefaultScalaModule)
    mapper.registerModule(new JavaTimeModule)
    mapper.registerModule(new Jdk8Module)
    mapper
  }

  /**
    * Register additional modules with the default mappers used for JSON and Smile.
    *
    * @param module
    *     Jackson databind module to register.
    */
  def registerModule(module: Module): Unit = {
    jsonMapper.registerModule(module)
    smileMapper.registerModule(module)
  }

  def newMapper: ObjectMapper = newMapper(jsonFactory)

  def newJsonGenerator(writer: Writer): JsonGenerator = {
    jsonFactory.createGenerator(writer)
  }

  def newJsonGenerator(stream: OutputStream): JsonGenerator = {
    jsonFactory.createGenerator(stream, JsonEncoding.UTF8)
  }

  def newJsonParser(reader: Reader): JsonParser = {
    jsonFactory.createParser(reader)
  }

  def newJsonParser(stream: InputStream): JsonParser = {
    jsonFactory.createParser(stream)
  }

  def newJsonParser(string: String): JsonParser = {
    jsonFactory.createParser(string)
  }

  def newJsonParser(bytes: Array[Byte]): JsonParser = {
    jsonFactory.createParser(bytes)
  }

  def newSmileGenerator(stream: OutputStream): JsonGenerator = {
    smileFactory.createGenerator(stream)
  }

  def newSmileParser(stream: InputStream): JsonParser = {
    smileFactory.createParser(stream)
  }

  def newSmileParser(bytes: Array[Byte]): JsonParser = {
    smileFactory.createParser(bytes, 0, bytes.length)
  }

  def encode[T: Manifest](obj: T): String = {
    jsonMapper.writeValueAsString(obj)
  }

  def encode[T: Manifest](writer: Writer, obj: T): Unit = {
    jsonMapper.writeValue(writer, obj)
  }

  def encode[T: Manifest](stream: OutputStream, obj: T): Unit = {
    jsonMapper.writeValue(stream, obj)
  }

  def encode[T: Manifest](gen: JsonGenerator, obj: T): Unit = {
    jsonMapper.writeValue(gen, obj)
  }

  def decodeResource[T: Manifest](name: String): T = {
    val url = getClass.getClassLoader.getResource(name)
    require(url != null, s"could not find resource: $name")
    val input = url.openStream()
    try decode[T](input)
    finally input.close()
  }

  def decode[T: Manifest](json: Array[Byte]): T = decoder[T].decode(json)

  def decode[T: Manifest](json: Array[Byte], offset: Int, length: Int): T =
    decoder[T].decode(json, offset, length)

  def decode[T: Manifest](json: String): T = decoder[T].decode(json)

  def decode[T: Manifest](reader: Reader): T = decoder[T].decode(reader)

  def decode[T: Manifest](stream: InputStream): T = decoder[T].decode(stream)

  def decode[T: Manifest](node: JsonNode): T = decoder[T].decode(node)

  def decode[T: Manifest](parser: JsonParser): T = {
    val reader: ObjectReader =
      if (manifest.runtimeClass.isArray)
        jsonMapper.readerFor(manifest.runtimeClass.asInstanceOf[Class[T]])
      else
        jsonMapper.readerFor(Reflection.typeReference[T])
    val value = reader.readValue[T](parser)
    require(parser.nextToken() == null, "invalid json, additional content after value")
    value
  }

  def decoder[T: Manifest]: Decoder[T] = {
    val reader: ObjectReader =
      if (manifest.runtimeClass.isArray)
        jsonMapper.readerFor(manifest.runtimeClass.asInstanceOf[Class[T]])
      else
        jsonMapper.readerFor(Reflection.typeReference[T])
    new Decoder[T](reader, jsonFactory)
  }

  def smileEncode[T: Manifest](obj: T): Array[Byte] = {
    smileMapper.writeValueAsBytes(obj)
  }

  def smileEncode[T: Manifest](stream: OutputStream, obj: T): Unit = {
    smileMapper.writeValue(stream, obj)
  }

  def smileDecode[T: Manifest](stream: InputStream): T = smileDecoder[T].decode(stream)

  def smileDecode[T: Manifest](json: Array[Byte]): T = smileDecoder[T].decode(json)

  def smileDecoder[T: Manifest]: Decoder[T] = {
    val reader: ObjectReader =
      if (manifest.runtimeClass.isArray)
        jsonMapper.readerFor(manifest.runtimeClass.asInstanceOf[Class[T]])
      else
        jsonMapper.readerFor(Reflection.typeReference[T])
    new Decoder[T](reader, smileFactory)
  }
}
